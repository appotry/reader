package io.legado.app.model.webBook

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.model.DebugLog
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.TextUtils

object BookChapterList {

    suspend fun analyzeChapterList(
//            coroutineScope: CoroutineScope,
        book: Book,
        body: String?,
        bookSource: BookSource,
        baseUrl: String,
        debugLog: DebugLog? = null
    ): List<BookChapter> {
        val chapterList = arrayListOf<BookChapter>()
        body ?: throw Exception(
//            App.INSTANCE.getString(R.string.error_get_web_content, baseUrl)
            //todo getString
            "error_get_web_content"
        )
        debugLog?.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        val tocRule = bookSource.getTocRule()
        val nextUrlList = arrayListOf(baseUrl)
        var reverse = false
        var listRule = tocRule.chapterList ?: ""
        if (listRule.startsWith("-")) {
            reverse = true
            listRule = listRule.substring(1)
        }
        if (listRule.startsWith("+")) {
            listRule = listRule.substring(1)
        }
        var chapterData =
            analyzeChapterList(
                book, baseUrl, body, tocRule, listRule, bookSource, debugLog = debugLog
            )
        chapterData.chapterList?.let {
            chapterList.addAll(it)
        }

        when (chapterData.nextUrl.size) {
            0 -> {
                return finish(book, chapterList, reverse)
            }
            1 -> {
                var nextUrl = chapterData.nextUrl[0]
                while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                    nextUrlList.add(nextUrl)
                    AnalyzeUrl(
                        ruleUrl = nextUrl,
                        ruleData = book,
                        headerMapF = bookSource.getHeaderMap()
                    ).getResponseAwait()
                        .body?.let { nextBody ->
                            chapterData = analyzeChapterList(
                                book, nextUrl, nextBody, tocRule, listRule, bookSource, debugLog = debugLog
                            )
                            nextUrl = if (chapterData.nextUrl.isNotEmpty()) {
                                chapterData.nextUrl[0]
                            } else ""
                            chapterData.chapterList?.let {
                                chapterList.addAll(it)
                            }
                        }
                }
                debugLog?.log(bookSource.bookSourceUrl, "◇目录总页数:${nextUrlList.size}")
                return finish(book, chapterList, reverse, debugLog = debugLog)
            }
            else -> {
                val chapterDataList = arrayListOf<ChapterData<String>>()
                for (item in chapterData.nextUrl) {
                    if (!nextUrlList.contains(item)) {
                        val data = ChapterData(nextUrl = item)
                        chapterDataList.add(data)
                        nextUrlList.add(item)
                    }
                }
                debugLog?.log(bookSource.bookSourceUrl, "◇目录总页数:${nextUrlList.size}")
                for (item in chapterDataList) {
                    downloadToc(
                        item,
                        book,
                        bookSource,
                        tocRule,
                        listRule,
                        chapterList,
                        chapterDataList,
                        debugLog = debugLog
                    )
                }
                return chapterList;
            }
        }
    }


    private suspend fun downloadToc(
        chapterData: ChapterData<String>,
        book: Book,
        bookSource: BookSource,
        tocRule: TocRule,
        listRule: String,
        chapterList: ArrayList<BookChapter>,
        chapterDataList: ArrayList<ChapterData<String>>,
        debugLog: DebugLog? = null
    ) {
        val nextBody = AnalyzeUrl(
            ruleUrl = chapterData.nextUrl,
            ruleData = book,
            headerMapF = bookSource.getHeaderMap()
        ).getResponseAwait(bookSource.bookSourceUrl).body
            ?: throw Exception("${chapterData.nextUrl}, 下载失败")
        val nextChapterData = analyzeChapterList(
            book, chapterData.nextUrl, nextBody, tocRule, listRule, bookSource,
            false, debugLog = debugLog
        )
        val isFinished = addChapterListIsFinish(
            chapterDataList,
            chapterData,
            nextChapterData.chapterList
        )
        if (isFinished) {
            chapterDataList.forEach { item ->
                item.chapterList?.let {
                    chapterList.addAll(it)
                }
            }
        }

    }


    private fun addChapterListIsFinish(
        chapterDataList: ArrayList<ChapterData<String>>,
        chapterData: ChapterData<String>,
        chapterList: List<BookChapter>?
    ): Boolean {
        chapterData.chapterList = chapterList
        chapterDataList.forEach {
            if (it.chapterList == null) {
                return false
            }
        }
        return true
    }


    private fun finish(
        book: Book,
        chapterList: ArrayList<BookChapter>,
        reverse: Boolean,
        debugLog: DebugLog? = null
    ): ArrayList<BookChapter> {
        //去重
        if (!reverse) {
            chapterList.reverse()
        }
        val lh = LinkedHashSet(chapterList)
        val list = ArrayList(lh)
        list.reverse()
        debugLog?.log(book.origin, "◇目录总数:${list.size}")
        for ((index, item) in list.withIndex()) {
            item.index = index
        }
        if (list.size > 0) {
            book.latestChapterTitle = list.last().title
        }
//        book.durChapterTitle =
//            list.getOrNull(book.durChapterIndex)?.title ?: book.latestChapterTitle
        if (book.totalChapterNum < list.size) {
            book.lastCheckCount = list.size - book.totalChapterNum
        }
        book.totalChapterNum = list.size
        return list
    }


    private fun analyzeChapterList(
        book: Book,
        baseUrl: String,
        body: String,
        tocRule: TocRule,
        listRule: String,
        bookSource: BookSource,
        getNextUrl: Boolean = true,
        debugLog: DebugLog? = null
    ): ChapterData<List<String>> {
        val analyzeRule = AnalyzeRule(book)
        analyzeRule.setContent(body, baseUrl)
        val chapterList = arrayListOf<BookChapter>()
        val nextUrlList = arrayListOf<String>()
        val nextTocRule = tocRule.nextTocUrl
        if (getNextUrl && !nextTocRule.isNullOrEmpty()) {
            debugLog?.log(bookSource.bookSourceUrl, "┌获取目录下一页列表")
            analyzeRule.getStringList(nextTocRule, true)?.let {
                for (item in it) {
                    if (item != baseUrl) {
                        nextUrlList.add(item)
                    }
                }
            }
            debugLog?.log(bookSource.bookSourceUrl, "└" + TextUtils.join("，\n", nextUrlList))
        }
        debugLog?.log(bookSource.bookSourceUrl, "┌获取目录列表")
        val elements = analyzeRule.getElements(listRule)
        debugLog?.log(bookSource.bookSourceUrl, "└列表大小:${elements.size}")
        if (elements.isNotEmpty()) {
            debugLog?.log(bookSource.bookSourceUrl, "┌获取首章名称")
            val nameRule = analyzeRule.splitSourceRule(tocRule.chapterName)
            val urlRule = analyzeRule.splitSourceRule(tocRule.chapterUrl)
            val vipRule = analyzeRule.splitSourceRule(tocRule.isVip)
            val update = analyzeRule.splitSourceRule(tocRule.updateTime)
            var isVip: String?
            for (item in elements) {
                analyzeRule.setContent(item)
                val bookChapter = BookChapter(bookUrl = book.bookUrl)
                analyzeRule.chapter = bookChapter
                bookChapter.title = analyzeRule.getString(nameRule)
                bookChapter.url = analyzeRule.getString(urlRule, true)
                bookChapter.tag = analyzeRule.getString(update)
                isVip = analyzeRule.getString(vipRule)
                if (bookChapter.url.isEmpty()) bookChapter.url = baseUrl
                if (bookChapter.title.isNotEmpty()) {
                    if (isVip.isNotEmpty() && isVip != "null" && isVip != "false" && isVip != "0") {
                        bookChapter.title = "\uD83D\uDD12" + bookChapter.title
                    }
                    chapterList.add(bookChapter)
                }
            }
            debugLog?.log(bookSource.bookSourceUrl, "└${chapterList[0].title}")
            debugLog?.log(bookSource.bookSourceUrl, "┌获取首章链接")
            debugLog?.log(bookSource.bookSourceUrl, "└${chapterList[0].url}")
            debugLog?.log(bookSource.bookSourceUrl, "┌获取首章信息")
            debugLog?.log(bookSource.bookSourceUrl, "└${chapterList[0].tag}")
        }
        return ChapterData(chapterList, nextUrlList)
    }

}
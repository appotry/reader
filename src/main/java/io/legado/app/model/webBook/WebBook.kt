package io.legado.app.model.webBook

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.storage.OldRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.BookChapterList
import io.legado.app.model.webBook.BookContent
import io.legado.app.model.webBook.BookInfo
import io.legado.app.model.webBook.BookList
import io.legado.app.model.Debug
import mu.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

class WebBook(val bookSource: BookSource, val debugLog: Boolean = true) {

    constructor(bookSourceString: String, debugLog: Boolean = true) : this(OldRule.jsonToBookSource(bookSourceString)!!, debugLog)

    val sourceUrl: String
        get() = bookSource.bookSourceUrl

    /**
     * 搜索
     */
    suspend fun searchBook(
        key: String,
        page: Int? = 1
    ): List<SearchBook> {
        return bookSource.searchUrl?.let { searchUrl ->
            val analyzeUrl = AnalyzeUrl(
                ruleUrl = searchUrl,
                key = key,
                page = page,
                baseUrl = sourceUrl,
                headerMapF = bookSource.getHeaderMap()
            )
            val res = analyzeUrl.getResponseAwait()
            BookList.analyzeBookList(
                res.body,
                bookSource,
                analyzeUrl,
                res.url,
                true,
                debugLog = if(debugLog) Debug else null
            ).map {
                it.tocHtml = ""
                it.infoHtml = ""
                it
            }
        } ?: arrayListOf()

    }

    /**
     * 发现
     */
    suspend fun exploreBook(
        url: String,
        page: Int? = 1
    ): List<SearchBook> {
        val analyzeUrl = AnalyzeUrl(
            ruleUrl = url,
            page = page,
            baseUrl = sourceUrl,
            headerMapF = bookSource.getHeaderMap()
        )
        val res = analyzeUrl.getResponseAwait()
        return BookList.analyzeBookList(
            res.body,
            bookSource,
            analyzeUrl,
            res.url,
            false,
            debugLog = if(debugLog) Debug else null
        )
    }

    /**
     * 书籍信息
     */
    suspend fun getBookInfo(bookUrl: String): Book {
        val book = Book()
        book.bookUrl = bookUrl
        book.origin = bookSource.bookSourceUrl
        book.originName = bookSource.bookSourceName
        book.originOrder = bookSource.customOrder
        book.type = bookSource.bookSourceType
        val analyzeUrl = AnalyzeUrl(
            ruleData = book,
            ruleUrl = book.bookUrl,
            baseUrl = sourceUrl,
            headerMapF = bookSource.getHeaderMap()
        )
        val body = analyzeUrl.getResponseAwait().body

        BookInfo.analyzeBookInfo(book, body, bookSource, book.bookUrl, debugLog = if(debugLog) Debug else null)
        book.tocHtml = null
        return book
    }

    /**
     * 目录
     */
    suspend fun getChapterList(
        book: Book
    ): List<BookChapter> {
        val body = if (book.bookUrl == book.tocUrl && !book.tocHtml.isNullOrEmpty()) {
            book.tocHtml
        } else {
            AnalyzeUrl(
                ruleData = book,
                ruleUrl = book.tocUrl,
//                baseUrl = book.bookUrl,
                headerMapF = bookSource.getHeaderMap()
            ).getResponseAwait().body
        }
        return BookChapterList.analyzeChapterList(book, body, bookSource, book.tocUrl, debugLog = if(debugLog) Debug else null)
    }

    /**
     * 章节内容
     */
    suspend fun getBookContent(
//        book: Book?,
//        bookChapter: BookChapter,
        bookChapterUrl:String,
        nextChapterUrl: String? = null
    ): String {
       if (bookSource.getContentRule().content.isNullOrEmpty()) {
           return bookChapterUrl
       }
//        val body = if (book != null && bookChapter.url == book.bookUrl && !book.tocHtml.isNullOrEmpty()) {
//            book.tocHtml
//        } else {
        val book = Book()
        val bookChapter = BookChapter()
        bookChapter.url = bookChapterUrl
        val analyzeUrl =
            AnalyzeUrl(
                ruleData = book,
                ruleUrl = bookChapter.url,
//                    baseUrl = book?.tocUrl,
                headerMapF = bookSource.getHeaderMap()
            )
        val body = analyzeUrl.getResponseAwait(
            bookSource.bookSourceUrl,
            jsStr = bookSource.getContentRule().webJs,
            sourceRegex = bookSource.getContentRule().sourceRegex
        ).body
//        }
        return BookContent.analyzeContent(
//                this,
            body,
            book,
            bookChapter,
            bookSource,
            bookChapter.url,
            nextChapterUrl,
            debugLog = if(debugLog) Debug else null
        )
    }
}
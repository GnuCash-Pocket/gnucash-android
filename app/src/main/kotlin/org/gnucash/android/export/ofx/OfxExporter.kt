/*
 * Copyright (c) 2012-2024 Gnucash Android Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.export.ofx

import android.content.Context
import android.preference.PreferenceManager
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.model.Account
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Exports the data in the database in OFX format.
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class OfxExporter(context: Context, params: ExportParams, bookUID: String) :
    Exporter(context, params, bookUID) {
    private lateinit var mAccountsList: List<Account>

    /**
     * Converts all expenses into OFX XML format and adds them to the XML document.
     *
     * @param doc DOM document of the OFX expenses.
     * @param parent Parent node for all expenses in report.
     */
    private fun generateOfx(doc: Document, parent: Element) {
        val transactionUid = doc.createElement(OfxHelper.TAG_TRANSACTION_UID)
        // Unsolicited because the data exported is not as a result of a request.
        transactionUid.appendChild(doc.createTextNode(OfxHelper.UNSOLICITED_TRANSACTION_ID))
        val statementTransactionResponse =
            doc.createElement(OfxHelper.TAG_STATEMENT_TRANSACTION_RESPONSE)
        statementTransactionResponse.appendChild(transactionUid)
        val bankmsgs = doc.createElement(OfxHelper.TAG_BANK_MESSAGES_V1)
        bankmsgs.appendChild(statementTransactionResponse)
        parent.appendChild(bankmsgs)
        mAccountsList
            .filter { it.transactionCount > 0 }
            .filter { GnuCashApplication.isDoubleEntryEnabled() || it.name?.contains ("Imbalance") != true }
            .forEach { account ->
                // Add account details (transactions) to the XML document.
                account.toOfx(doc, statementTransactionResponse, mExportParams.exportStartTime)
                // Mark as exported.
                mAccountsDbAdapter.markAsExported(account.uID)
            }
    }

    /**
     * Generate OFX export file from the transactions in the database.
     *
     * @return String containing OFX export.
     * @throws ExporterException if an XML builder could not be created.
     */
    @Throws(ExporterException::class)
    private fun generateOfxExport(): String {
        val document = makeDocBuilder().newDocument()
        val root = document.createElement("OFX")
        val pi = document.createProcessingInstruction("OFX", OfxHelper.OFX_HEADER)
        document.appendChild(pi)
        document.appendChild(root)
        generateOfx(document, root)
        val useXmlHeader = PreferenceManager.getDefaultSharedPreferences(mContext)
            .getBoolean(mContext.getString(R.string.key_xml_ofx_header), false)
        PreferencesHelper.setLastExportTime(TimestampHelper.getTimestampFromNow())
        val stringWriter = StringWriter()
        if (useXmlHeader) {
            write(document, stringWriter, false)
            return stringWriter.toString()
        }
        // If we want SGML OFX headers, write first to string and then prepend header.
        val ofxNode = document.getElementsByTagName("OFX").item(0)
        write(ofxNode, stringWriter, true)
        return OfxHelper.OFX_SGML_HEADER + "\n" + stringWriter.toString()
    }

    @Throws(ExporterException::class)
    private fun makeDocBuilder(): DocumentBuilder {
        val docFactory = DocumentBuilderFactory.newInstance()
        try {
            return docFactory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            throw ExporterException(mExportParams, e)
        }
    }

    @Throws(ExporterException::class)
    override fun generateExport(): List<String> {
        mAccountsList = mAccountsDbAdapter.getExportableAccounts(mExportParams.exportStartTime)
        if (mAccountsList.isEmpty()) { // Nothing to export, so no files generated
            close()
            return listOf()
        }
        var writer: BufferedWriter? = null
        try {
            val file = File(exportCacheFilePath)
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8"))
            writer.write(generateOfxExport())
            close()
        } catch (e: IOException) {
            throw ExporterException(mExportParams, e)
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (e: IOException) {
                    throw ExporterException(mExportParams, e)
                }
            }
        }
        return listOf(exportCacheFilePath)
    }

    /**
     * Writes out the document held in `node` to `outputWriter`
     *
     * @param node Node, containing the OFX document structure. Usually the parent node.
     * @param outputWriter Writer to use in writing the file to stream.
     * @param omitXmlDeclaration Flag which causes the XML declaration to be omitted.
     */
    private fun write(node: Node, outputWriter: Writer, omitXmlDeclaration: Boolean) {
        try {
            val transformerFactory = TransformerFactory .newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(node)
            val result = StreamResult(outputWriter)
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            if (omitXmlDeclaration) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }
            transformer.transform(source, result)
        } catch (e: TransformerException) {
            Timber.e(e)
            throw ExporterException(mExportParams, e)
        }
    }

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    override fun getExportMimeType(): String {
        return "text/xml"
    }
}

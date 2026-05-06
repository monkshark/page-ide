package page.editor

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

object SvgFormatter {
    fun prettyPrint(xml: String): String? {
        val trimmed = xml.trim()
        if (trimmed.isEmpty()) return null
        val pretty = runCatching {
            val builder = DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(trimmed)))
            doc.normalize()
            stripWhitespaceTextNodes(doc.documentElement)
            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))
            writer.toString().trim()
        }.getOrNull() ?: return null
        return pretty.takeIf { it.isNotEmpty() && it != trimmed }
    }

    private fun stripWhitespaceTextNodes(node: org.w3c.dom.Node) {
        val children = node.childNodes
        val toRemove = mutableListOf<org.w3c.dom.Node>()
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                if (child.textContent.isNullOrBlank()) toRemove += child
            } else if (child.hasChildNodes()) {
                stripWhitespaceTextNodes(child)
            }
        }
        toRemove.forEach { node.removeChild(it) }
    }
}

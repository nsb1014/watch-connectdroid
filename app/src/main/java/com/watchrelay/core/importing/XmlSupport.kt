package com.watchrelay.core.importing

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XmlSupport {
    fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isIgnoringComments = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(bytes))
    }

    fun Element.children(tag: String): List<Element> {
        val wanted = tag.lowercase()
        val out = mutableListOf<Element>()
        val list: NodeList = childNodes
        for (i in 0 until list.length) {
            val node = list.item(i)
            if (node is Element && node.localOrName() == wanted) {
                out += node
            }
        }
        return out
    }

    fun Element.descendants(tag: String): List<Element> {
        val wanted = tag.lowercase()
        val out = mutableListOf<Element>()
        val kids = childNodes
        for (i in 0 until kids.length) {
            collect(kids.item(i), wanted, out)
        }
        return out
    }

    fun Element.textOf(tag: String): String? =
        children(tag).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    fun Element.attr(name: String): String? {
        val direct = getAttribute(name)
        if (direct.isNotBlank()) return direct
        val named = attributes ?: return null
        for (i in 0 until named.length) {
            val item = named.item(i)
            if (item.nodeName.substringAfter(':').equals(name, ignoreCase = true)) {
                return item.nodeValue
            }
        }
        return null
    }

    fun Element.localOrName(): String = (localName ?: tagName.substringAfter(':')).lowercase()

    private fun collect(node: Node, wanted: String, out: MutableList<Element>) {
        if (node is Element && node.localOrName() == wanted) {
            out += node
        }
        val kids = node.childNodes
        for (i in 0 until kids.length) {
            collect(kids.item(i), wanted, out)
        }
    }
}

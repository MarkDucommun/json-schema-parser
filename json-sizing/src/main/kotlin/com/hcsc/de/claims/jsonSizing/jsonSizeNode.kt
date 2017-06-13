package com.hcsc.de.claims.jsonSizing

sealed class JsonSizeNode {
    abstract val name: String
    abstract val size: Int
    abstract val empty: Boolean
}

data class JsonSizeLeafNode(
        override val name: String,
        override val size: Int
) : com.hcsc.de.claims.jsonSizing.JsonSizeNode() {
    override val empty: Boolean get() = size == 0
}

data class JsonSizeObject(
        override val name: String,
        override val size: Int,
        val children: List<com.hcsc.de.claims.jsonSizing.JsonSizeNode>
) : com.hcsc.de.claims.jsonSizing.JsonSizeNode() {
    override val empty: Boolean get() = children.isEmpty()
}

data class JsonSizeArray(
        override val name: String,
        override val size: Int,
        val children: List<com.hcsc.de.claims.jsonSizing.JsonSizeNode>
) : com.hcsc.de.claims.jsonSizing.JsonSizeNode() {
    override val empty: Boolean get() = children.isEmpty()
}

data class JsonSizeEmpty(
        override val name: String
) : com.hcsc.de.claims.jsonSizing.JsonSizeNode() {
    override val size: Int = 0
    override val empty: Boolean = true
}
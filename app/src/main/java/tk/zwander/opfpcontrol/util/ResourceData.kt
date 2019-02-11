package tk.zwander.opfpcontrol.util

data class ResourceData(
        val type: String,
        val name: String,
        val value: String,
        val otherData: String = ""
)
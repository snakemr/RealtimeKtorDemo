enum class Action { Lock, Unlock, Update, Insert, Delete }

data class DataAction <out T>(val action: Action, val data: T)

data class User(val id: Long, val name: String)

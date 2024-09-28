import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val client = remember { RealClient() }
    val scope = rememberCoroutineScope()

    fun delete(id: Long) = scope.launch { runCatching { client.delete(id) } }
    fun insert(name: String) = scope.launch { runCatching { client.insert(name) } }
    fun update(user: User) = scope.launch { runCatching { client.update(user) } }

    val users by produceState(mutableStateListOf()) {
        runCatching {
            withContext(Dispatchers.IO) {
                client.users()
            }
        }.onFailure {
            println(it.message)
        }.onSuccess {
            value = it.toMutableStateList()
        }
    }

    var dialog by remember { mutableStateOf<Action?>(null) }
    var edit by remember { mutableStateOf<User?>(null) }
    fun close() = scope.launch {
        dialog = null
        edit = null
    }

    LaunchedEffect(users) {
        client.flow().flowOn(Dispatchers.IO).catch {
            println(it.message)
        }.collect { (action, data) ->
            if (data.id == edit?.id) close()
            when(action) {
                Action.Insert, Action.Update -> {
                    val index = users.indexOfFirst { data.id == it.id }
                    if (index == -1)
                        users += data
                    else
                        users[index] = data
                }
                Action.Delete -> users.removeIf { data.id == it.id }
            }
        }
    }

    MaterialTheme {
        LazyColumn {
            items(users.sortedBy { it.id }, { it.id }) { user ->
                Column(Modifier.animateItemPlacement()) {
                    val state = rememberSwipeToDismissBoxState()
                    LaunchedEffect(edit) {
                        if (edit == null) state.reset()
                    }
                    LaunchedEffect(state.currentValue) {
                        if (state.currentValue == SwipeToDismissBoxValue.EndToStart)
                            delete(user.id)
                        else if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                            edit = user
                            dialog = Action.Update
                        }
                    }
                    SwipeToDismissBox(state, {}) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.LightGray)
                            Text(user.name, Modifier.padding(start = 8.dp).weight(1f))
                            IconButton({ edit = user; dialog = Action.Update }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton({ delete(user.id) }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.error)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.LightGray)
                        }
                    }
                    HorizontalDivider()
                }
            }
            item {
                IconButton({ edit = null ; dialog = Action.Insert }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (dialog != null) {
            var name by remember { mutableStateOf(TextFieldValue(edit?.name ?: "")) }
            fun action() {
                if (name.text.isNotEmpty()) when(dialog) {
                    Action.Insert -> insert(name.text)
                    Action.Update -> edit?.copy(name = name.text)?.let(::update)
                    else -> {}
                }
                close()
            }

            val requester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                requester.requestFocus()
                name = name.copy(selection = TextRange(0, name.text.length))
            }

            AlertDialog(
                onDismissRequest = ::close,
                title = { Text("User name") },
                text = {
                    OutlinedTextField(name, { name = it }, Modifier.focusRequester(requester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions { action() },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(::action) { Text(dialog.toString()) }
                }
            )
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = DpSize(400.dp, 800.dp))
    ) {
        App()
    }
}

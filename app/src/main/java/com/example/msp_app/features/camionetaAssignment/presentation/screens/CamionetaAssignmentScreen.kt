package com.example.msp_app.features.camionetaAssignment.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.msp_app.components.CompactSearchField
import com.example.msp_app.components.DrawerContainer
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.matchesFuzzy
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.features.camionetaAssignment.domain.models.Camioneta
import com.example.msp_app.features.camionetaAssignment.domain.models.UsuarioAsignado
import com.example.msp_app.features.camionetaAssignment.presentation.viewmodels.CamionetaAssignmentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamionetaAssignmentScreen(
    navController: NavController,
    viewModel: CamionetaAssignmentViewModel = viewModel()
) {
    val camionetasState by viewModel.camionetasState.collectAsState()
    val usuariosSinAsignarState by viewModel.usuariosSinAsignarState.collectAsState()
    val assignmentState by viewModel.assignmentState.collectAsState()
    val showAssignDialog by viewModel.showAssignDialog.collectAsState()
    val showUnassignDialog by viewModel.showUnassignDialog.collectAsState()
    val selectedCamioneta by viewModel.selectedCamioneta.collectAsState()
    val userToUnassign by viewModel.userToUnassign.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Handle assignment state changes
    LaunchedEffect(assignmentState) {
        when (assignmentState) {
            is ResultState.Success -> {
                snackbarHostState.showSnackbar("Operacion realizada exitosamente")
                viewModel.clearAssignmentState()
            }
            is ResultState.Error -> {
                snackbarHostState.showSnackbar((assignmentState as ResultState.Error).message)
                viewModel.clearAssignmentState()
            }
            else -> {}
        }
    }

    DrawerContainer(navController = navController) { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Asignacion de Camionetas",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { openDrawer() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadCamionetas() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar"
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (camionetasState) {
                is ResultState.Loading, ResultState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResultState.Error -> {
                    ErrorContent(
                        message = (camionetasState as ResultState.Error).message,
                        onRetry = { viewModel.loadCamionetas() }
                    )
                }

                is ResultState.Success -> {
                    val camionetas = (camionetasState as ResultState.Success<List<Camioneta>>).data

                    if (camionetas.isEmpty()) {
                        EmptyContent()
                    } else {
                        CamionetasList(
                            camionetas = camionetas,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onAssignClick = { camioneta -> viewModel.openAssignDialog(camioneta) },
                            onUnassignClick = { userId, userName ->
                                viewModel.openUnassignDialog(userId, userName)
                            }
                        )
                    }
                }

                is ResultState.Offline -> {
                    val camionetas = (camionetasState as ResultState.Offline<List<Camioneta>>).data
                    CamionetasList(
                        camionetas = camionetas,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onAssignClick = { camioneta -> viewModel.openAssignDialog(camioneta) },
                        onUnassignClick = { userId, userName ->
                            viewModel.openUnassignDialog(userId, userName)
                        }
                    )
                }
            }

            // Loading overlay for assignment operations
            if (assignmentState is ResultState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    }

    // Assignment Dialog
    if (showAssignDialog && selectedCamioneta != null) {
        AssignUserDialog(
            camioneta = selectedCamioneta!!,
            usuariosState = usuariosSinAsignarState,
            onDismiss = { viewModel.closeAssignDialog() },
            onAssign = { userId ->
                viewModel.asignarUsuario(userId, selectedCamioneta!!.almacenId)
            }
        )
    }

    // Unassign Confirmation Dialog
    if (showUnassignDialog && userToUnassign != null) {
        UnassignConfirmationDialog(
            userName = userToUnassign!!.second,
            onDismiss = { viewModel.closeUnassignDialog() },
            onConfirm = { viewModel.desasignarUsuario(userToUnassign!!.first) }
        )
    }
}

@Composable
private fun CamionetasList(
    camionetas: List<Camioneta>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAssignClick: (Camioneta) -> Unit,
    onUnassignClick: (userId: String, userName: String) -> Unit
) {
    val filteredCamionetas = if (searchQuery.isBlank()) {
        camionetas
    } else {
        camionetas.filter { camioneta ->
            camioneta.nombre.matchesFuzzy(searchQuery) ||
            camioneta.usuariosAsignados.any { usuario ->
                usuario.nombre.matchesFuzzy(searchQuery)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        CompactSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Buscar camioneta o vendedor...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "${filteredCamionetas.size} camionetas encontradas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (filteredCamionetas.isEmpty()) {
                item {
                    Text(
                        text = "No se encontraron resultados para \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(
                    items = filteredCamionetas,
                    key = { it.almacenId }
                ) { camioneta ->
                    CamionetaCard(
                        camioneta = camioneta,
                        onAssignClick = { onAssignClick(camioneta) },
                        onUnassignClick = onUnassignClick
                    )
                }
            }
        }
    }
}

@Composable
private fun CamionetaCard(
    camioneta: Camioneta,
    onAssignClick: () -> Unit,
    onUnassignClick: (userId: String, userName: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = camioneta.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: ${camioneta.almacenId} | ${camioneta.existencias} productos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Capacity indicator
                CapacityIndicator(
                    assigned = camioneta.usuariosAsignados.size,
                    max = Camioneta.MAX_USUARIOS_POR_CAMIONETA
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Assigned Users Section
            Text(
                text = "Usuarios asignados:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (camioneta.usuariosAsignados.isEmpty()) {
                Text(
                    text = "Sin usuarios asignados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    camioneta.usuariosAsignados.forEach { usuario ->
                        AssignedUserItem(
                            usuario = usuario,
                            onRemoveClick = { onUnassignClick(usuario.id, usuario.nombre) }
                        )
                    }
                }
            }

            // Add user button
            if (camioneta.puedeAceptarMasUsuarios()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAssignClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Asignar usuario")
                }
            }
        }
    }
}

@Composable
private fun CapacityIndicator(
    assigned: Int,
    max: Int
) {
    val color = when {
        assigned >= max -> MaterialTheme.colorScheme.error
        assigned == max - 1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$assigned/$max",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun AssignedUserItem(
    usuario: UsuarioAsignado,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = usuario.nombre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Cobrador #${usuario.cobradorId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remover asignacion",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AssignUserDialog(
    camioneta: Camioneta,
    usuariosState: ResultState<List<User>>,
    onDismiss: () -> Unit,
    onAssign: (userId: String) -> Unit
) {
    var userSearchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Asignar a ${camioneta.nombre}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Selecciona un usuario para asignar a esta camioneta:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Search field for users
                CompactSearchField(
                    value = userSearchQuery,
                    onValueChange = { userSearchQuery = it },
                    placeholder = "Buscar usuario..."
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (usuariosState) {
                    is ResultState.Loading, ResultState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is ResultState.Error -> {
                        Text(
                            text = "Error: ${(usuariosState as ResultState.Error).message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    is ResultState.Success -> {
                        val usuarios = (usuariosState as ResultState.Success<List<User>>).data
                        val filteredUsuarios = if (userSearchQuery.isBlank()) {
                            usuarios
                        } else {
                            usuarios.filter { user ->
                                user.NOMBRE.matchesFuzzy(userSearchQuery)
                            }
                        }

                        if (usuarios.isEmpty()) {
                            Text(
                                text = "No hay usuarios disponibles para asignar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (filteredUsuarios.isEmpty()) {
                            Text(
                                text = "No se encontraron usuarios para \"$userSearchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(250.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = filteredUsuarios,
                                    key = { it.ID }
                                ) { user ->
                                    UserSelectItem(
                                        user = user,
                                        onClick = { onAssign(user.ID) }
                                    )
                                }
                            }
                        }
                    }

                    is ResultState.Offline -> {
                        val usuarios = (usuariosState as ResultState.Offline<List<User>>).data
                        val filteredUsuarios = if (userSearchQuery.isBlank()) {
                            usuarios
                        } else {
                            usuarios.filter { user ->
                                user.NOMBRE.matchesFuzzy(userSearchQuery)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.height(250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = filteredUsuarios,
                                key = { it.ID }
                            ) { user ->
                                UserSelectItem(
                                    user = user,
                                    onClick = { onAssign(user.ID) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun UserSelectItem(
    user: User,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = user.NOMBRE,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Cobrador #${user.COBRADOR_ID} | Zona ${user.ZONA_CLIENTE_ID}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UnassignConfirmationDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Confirmar desasignacion",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "¿Estas seguro de que deseas remover la asignacion de $userName?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reintentar")
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sin camionetas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No hay camionetas disponibles para asignar",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

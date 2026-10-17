package app.mingo.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mingo.R
import app.mingo.ui.formatBytes
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons

/** Shows whatever the update flow needs from the user; renders nothing while idle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back from the "install unknown apps" screen re-checks the permission.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) manager.onPermissionResult()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = manager::dismiss,
            icon = { MingoIcon(MingoIcons.Sparkles) },
            title = { Text(stringResource(R.string.update_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.update_message, s.release.version, manager.currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.release.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { manager.download(s.release) }) {
                    Text(stringResource(R.string.action_update) + "  ·  " + formatBytes(s.release.apkSize))
                }
            },
            dismissButton = { TextButton(onClick = manager::dismiss) { Text(stringResource(R.string.action_later)) } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            icon = { MingoIcon(MingoIcons.Download) },
            title = { Text(stringResource(R.string.update_downloading, (s.progress * 100).toInt())) },
            text = { LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = manager::dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = manager::dismiss,
            icon = { MingoIcon(MingoIcons.Shield) },
            title = { Text(stringResource(R.string.update_permission)) },
            text = { Text(stringResource(R.string.update_permission_desc)) },
            confirmButton = {
                Button(onClick = { context.startActivity(manager.permissionIntent()) }) {
                    Text(stringResource(R.string.action_open_settings))
                }
            },
            dismissButton = { TextButton(onClick = manager::dismiss) { Text(stringResource(R.string.action_later)) } },
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = manager::dismiss,
            icon = { MingoIcon(MingoIcons.CheckCircle) },
            title = { Text(stringResource(R.string.update_ready, s.release.version)) },
            text = { Text(stringResource(R.string.update_ready_desc)) },
            confirmButton = {
                Button(onClick = { manager.install(context, s.file) }) { Text(stringResource(R.string.action_install)) }
            },
            dismissButton = { TextButton(onClick = manager::dismiss) { Text(stringResource(R.string.action_later)) } },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = manager::dismiss,
            icon = { MingoIcon(MingoIcons.Alert) },
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(s.message, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = manager::dismiss) { Text(stringResource(R.string.action_dismiss)) } },
        )

        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> Unit
    }
}

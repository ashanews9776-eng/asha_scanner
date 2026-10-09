package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.ahoura.asha_scanner_ip.core.guard.AppEntry
import com.ahoura.asha_scanner_ip.core.guard.SplitTunnelSettings
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberToggle
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC

@Composable
fun SplitTunnelScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val splitSettings = remember { SplitTunnelSettings(context) }

    var mode by remember { mutableStateOf(splitSettings.mode()) }
    var selectedPkgs by remember { mutableStateOf(splitSettings.packages().toMutableSet()) }

    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = SplitTunnelSettings.loadInstalledApps(context)
        allApps = loaded
        loading = false
    }

    fun persist(newMode: SplitTunnelSettings.Mode, newPkgs: Set<String>) {
        mode = newMode
        selectedPkgs = newPkgs.toMutableSet()
        splitSettings.save(newMode, newPkgs)
    }

    val filteredApps = remember(allApps, searchQuery, showSystem) {
        allApps.filter { app ->
            (showSystem || !app.isSystem) &&
                    (searchQuery.isBlank() ||
                            app.label.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        CyberAppBar(title = s.splitTunnelTitle, onBack = onBack)

        Text(
            text = s.splitTunnelSubtitle,
            color = TextMutedC,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTab(
                label = s.splitModeAll,
                selected = mode == SplitTunnelSettings.Mode.ALL,
                onClick = { persist(SplitTunnelSettings.Mode.ALL, selectedPkgs) },
                modifier = Modifier.weight(1f)
            )
            ModeTab(
                label = s.splitModeInclude,
                selected = mode == SplitTunnelSettings.Mode.INCLUDE,
                onClick = { persist(SplitTunnelSettings.Mode.INCLUDE, selectedPkgs) },
                modifier = Modifier.weight(1f)
            )
            ModeTab(
                label = s.splitModeExclude,
                selected = mode == SplitTunnelSettings.Mode.EXCLUDE,
                onClick = { persist(SplitTunnelSettings.Mode.EXCLUDE, selectedPkgs) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (mode != SplitTunnelSettings.Mode.ALL) {
            // Search & Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(s.searchApps, color = TextMutedC, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Accent, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Clear,
                                null,
                                tint = TextMutedC,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceC,
                        unfocusedContainerColor = SurfaceC,
                        focusedIndicatorColor = Accent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimaryC,
                        unfocusedTextColor = TextPrimaryC,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(0.5.dp, BorderC, RoundedCornerShape(8.dp))
                )
            }

            Spacer(Modifier.height(8.dp))

            // Sub-bar: stats, select all, system toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedPkgs.size} ${s.appsSelected}",
                    color = Accent,
                    fontFamily = ShareTechMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = s.showSystemApps,
                        color = TextSecondaryC,
                        fontSize = 11.sp,
                    )
                    CyberToggle(
                        checked = showSystem,
                        onChange = { showSystem = it }
                    )

                    Text(
                        text = if (selectedPkgs.containsAll(filteredApps.map { it.packageName })) s.deselectAll else s.selectAll,
                        color = AccentDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val newSet = selectedPkgs.toMutableSet()
                            val visiblePkgs = filteredApps.map { it.packageName }
                            if (newSet.containsAll(visiblePkgs)) {
                                newSet.removeAll(visiblePkgs.toSet())
                            } else {
                                newSet.addAll(visiblePkgs)
                            }
                            persist(mode, newSet)
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // App List
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
            }
        } else if (mode == SplitTunnelSettings.Mode.ALL) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Apps, null, tint = Accent.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = s.splitModeAll,
                        color = TextPrimaryC,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = s.splitTunnelSubtitle,
                        color = TextMutedC,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.noAppsFound, color = TextMutedC, fontSize = 13.sp)
            }
        } else {
            val pm = context.packageManager
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = selectedPkgs.contains(app.packageName)
                    val iconBitmap = remember(app.packageName) {
                        runCatching {
                            pm.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
                        }.getOrNull()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceC)
                            .border(
                                0.5.dp,
                                if (isChecked) Accent.copy(alpha = 0.5f) else BorderC,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val next = selectedPkgs.toMutableSet()
                                if (isChecked) next.remove(app.packageName) else next.add(app.packageName)
                                persist(mode, next)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BorderC),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Apps, null, tint = TextMutedC, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                color = TextPrimaryC,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                color = TextMutedC,
                                fontSize = 10.sp,
                                fontFamily = ShareTechMono,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val next = selectedPkgs.toMutableSet()
                                if (checked) next.add(app.packageName) else next.remove(app.packageName)
                                persist(mode, next)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Accent,
                                checkmarkColor = Color.Black,
                                uncheckedColor = BorderC
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(0.5.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Accent else TextMutedC,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

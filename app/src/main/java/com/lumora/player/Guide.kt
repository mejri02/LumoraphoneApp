@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lumora.player

import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val BEIN_EPG_URL = "https://raw.githubusercontent.com/ayoubboukous27/Bein-Sports-MENA-epg/main/epg_mapped_with_logos.xml"
private data class GuideChannel(val id: String, val name: String, val logo: String)
private data class GuideProgramme(val channelId: String, val title: String, val start: Long, val stop: Long)
private data class GuideData(val channels: List<GuideChannel>, val programmes: List<GuideProgramme>)

private fun parseXmlTvTime(raw: String): Long {
    for (pattern in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")) {
        try {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            if (pattern == "yyyyMMddHHmmss") fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.parse(raw.trim())?.time ?: 0L
        } catch (_: Exception) { }
    }
    return 0L
}

private suspend fun loadBeinGuide(): GuideData = withContext(Dispatchers.IO) {
    val connection = (URL(BEIN_EPG_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 25000
        setRequestProperty("User-Agent", "LumoraTV/1.0")
    }
    try {
        connection.inputStream.use { input ->
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(input, "UTF-8")
            }
            val channels = mutableListOf<GuideChannel>()
            val programmes = mutableListOf<GuideProgramme>()
            var event = parser.eventType
            var channelId: String? = null
            var channelName = ""
            var channelLogo = ""
            var programmeChannel = ""
            var programmeTitle = ""
            var programmeStart = 0L
            var programmeStop = 0L
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "channel" -> { channelId = parser.getAttributeValue(null, "id").orEmpty(); channelName = ""; channelLogo = "" }
                        "display-name" -> if (channelId != null) channelName = parser.nextText().trim()
                        "icon" -> if (channelId != null) channelLogo = parser.getAttributeValue(null, "src").orEmpty()
                        "programme" -> {
                            programmeChannel = parser.getAttributeValue(null, "channel").orEmpty()
                            programmeStart = parseXmlTvTime(parser.getAttributeValue(null, "start").orEmpty())
                            programmeStop = parseXmlTvTime(parser.getAttributeValue(null, "stop").orEmpty())
                            programmeTitle = ""
                        }
                        "title" -> if (programmeChannel.isNotBlank()) programmeTitle = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "channel" -> {
                            val id = channelId.orEmpty()
                            if (id.isNotBlank() && channelName.isNotBlank()) channels.add(GuideChannel(id, channelName, channelLogo))
                            channelId = null
                        }
                        "programme" -> if (programmeChannel.isNotBlank() && programmeTitle.isNotBlank() && programmeStop > programmeStart) {
                            programmes.add(GuideProgramme(programmeChannel, programmeTitle, programmeStart, programmeStop))
                            programmeChannel = ""
                        }
                    }
                }
                event = parser.next()
            }
            GuideData(channels.sortedBy { it.name }, programmes)
        }
    } finally { connection.disconnect() }
}

@Composable
fun GuideTab() {
    var data by remember { mutableStateOf<GuideData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        try { data = loadBeinGuide() } catch (e: Exception) { error = e.message ?: "Unable to load the guide" }
        finally { loading = false }
    }
    val now = System.currentTimeMillis()
    val channels = data?.channels.orEmpty().filter {
        (filter == "All" || it.name.contains(filter, ignoreCase = true)) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }
    val programmeMap = remember(data, now) {
        data?.channels.orEmpty().associate { ch ->
            ch.id to data!!.programmes.filter { it.channelId == ch.id && it.stop > now }.sortedBy { it.start }.take(4)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("beIN Sports Guide", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Programme schedule · MENA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refreshKey++ }, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Refresh guide")
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, label = { Text("Search channels") })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "SPORTS", "MAX", "XTRA").forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(f) }) }
        }
        when {
            loading && data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null && data == null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load the guide", fontWeight = FontWeight.SemiBold)
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Button(onClick = { refreshKey++ }, Modifier.padding(top = 12.dp)) { Text("Try again") }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(channels, key = { it.id }) { channel ->
                    val schedule = programmeMap[channel.id].orEmpty()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (channel.logo.isNotBlank()) AsyncImage(channel.logo, channel.name, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                            else Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("TV", fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            Text(channel.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (schedule.isEmpty()) Text("No upcoming programme data", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        else schedule.forEachIndexed { index, programme ->
                            val isNow = programme.start <= now && programme.stop > now
                            val time = remember(programme.start, programme.stop) {
                                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                                fmt.format(Date(programme.start)) + " – " + fmt.format(Date(programme.stop))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 9.dp).then(if (isNow) Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(7.dp) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                                Text(time, Modifier.width(104.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(programme.title, fontSize = 13.sp, fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (isNow) Text("NOW", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    else if (index == 0 && programme.start > now) Text("NEXT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (channels.isEmpty()) item { Text("No matching channels", Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text("Guide data is loaded from the public EPG XML source.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

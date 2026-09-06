package com.watchrelay.app.wifi

import fi.iki.elonen.NanoHTTPD
import java.io.File

class RelayHttpServer(
    private val onImport: (fileName: String, bytes: ByteArray) -> String
) : NanoHTTPD(LanAddress.PORT) {

    @Volatile
    var lastMessage: String = "Waiting for a file…"

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.uri == "/api/status" -> {
                json("""{"listening":true,"message":${lastMessage.asJson()}}""")
            }
            session.method == Method.POST && (session.uri == "/import" || session.uri == "/") -> {
                handleUpload(session)
            }
            else -> html(UPLOAD_PAGE)
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val uploaded = files.values.firstOrNull { it.isNotBlank() }
                ?: return html("No file in the request.", NanoHTTPD.Response.Status.BAD_REQUEST)
            val bytes = File(uploaded).readBytes()
            val name = session.parms["file"]
                ?: session.headers["file-name"]
                ?: guessName(session, bytes)
            val result = onImport(name, bytes)
            lastMessage = result
            html(resultPage(result))
        } catch (error: Exception) {
            lastMessage = error.message ?: "Import failed"
            html("Import failed: ${error.message}", NanoHTTPD.Response.Status.INTERNAL_ERROR)
        }
    }

    private fun guessName(session: IHTTPSession, bytes: ByteArray): String {
        val disposition = session.headers["content-disposition"].orEmpty()
        val match = Regex("filename=\"?([^\";]+)", RegexOption.IGNORE_CASE).find(disposition)
        if (match != null) return match.groupValues[1]
        return if (bytes.size >= 12 && String(bytes, 8, 4) == ".FIT") "watch.fit" else "watch-export.bin"
    }

    private fun html(body: String, status: Response.IStatus = Response.Status.OK): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", body)

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)

    private fun String.asJson(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        private val UPLOAD_PAGE = """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>WatchRelay</title>
            <style>
              body{font-family:-apple-system,system-ui,sans-serif;background:#0B1117;color:#E8EEF4;margin:0;padding:32px}
              .card{max-width:420px;margin:40px auto;background:#151C24;border-radius:20px;padding:28px}
              h1{font-size:22px;margin:0 0 8px}
              p{color:#9AA8B6;line-height:1.5}
              input,button{width:100%;margin-top:12px;padding:14px;border-radius:12px;border:0;font-size:16px}
              button{background:#3DDC97;color:#0B1117;font-weight:700}
            </style></head>
            <body><div class="card">
              <h1>Send a workout</h1>
              <p>Export from Apple Watch / Health on this Wi‑Fi, then drop the file here. GPX, TCX, FIT, JSON, CSV, and Health export ZIP/XML all work.</p>
              <form method="post" action="/import" enctype="multipart/form-data">
                <input type="file" name="file" required>
                <button type="submit">Import to phone</button>
              </form>
            </div></body></html>
        """.trimIndent()

        private fun resultPage(message: String): String = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>WatchRelay</title>
            <style>body{font-family:-apple-system,system-ui;background:#0B1117;color:#E8EEF4;padding:40px}</style>
            </head><body><p>$message</p><p><a href="/" style="color:#3DDC97">Send another</a></p></body></html>
        """.trimIndent()
    }
}

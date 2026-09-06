package com.github.nndwn.todoso

object TodosoConstant {
    const val GITHUB_URL = "https://github.com/nndwn/todoso"
    const val PLUGIN_ID = "com.github.nndwn.todoso"
    const val PLUGIN_NAME = "Todoso"

    fun getInstructionHtml(): String = """
        <html>
        <body style="font-family: sans-serif; padding: 12px; color: #BBBBBB;">
            <h1 style="margin-top: 0; color: #FFFFFF;">${TodosoBundle.message("instruction.welcome.title")}</h1>
            <p>${TodosoBundle.message("instruction.empty.desc")}</p>
            <ul>
                <li>${TodosoBundle.message("instruction.step.submit")}</li>
                <li>${TodosoBundle.message("instruction.step.context")}</li>
                <li>
                    ${TodosoBundle.message("instruction.step.format")}<br/>
                    <code style="background-color: #2B2D30; color: #A9B7C6; padding: 2px 4px;">[H] task description #tags</code>
                </li>
            </ul>
            <p>${TodosoBundle.message("instruction.doc.link", GITHUB_URL)}</p>
        </body>
        </html>
    """.trimIndent()
}
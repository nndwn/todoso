package com.github.nndwn.todoso

object TodosoConstants {
  const val GITHUB_REPO_URL = "https://github.com/nndwn/todoso"
  const val PLUGIN_ID = "com.github.nndwn.todoso"
  const val PLUGIN_NAME = "Todoso: Markdown Todo List"
  const val FILENAME = "TODO.md"

  private val EXCLUSIVE_RELATIONS = listOf(
    listOf("feature", "issue"),
    listOf("development", "production")
  )

  private val STANDALONE_TAGS = listOf("urgent")

  val DEFAULT_QUICK_TAGS = EXCLUSIVE_RELATIONS.flatten() + STANDALONE_TAGS

  val EXCLUSIVE_TAG_GROUPS: Map<String, List<String>> = EXCLUSIVE_RELATIONS.flatMap { group ->
    group.map { tag -> tag to group.filter { it != tag } }
  }.toMap()
  fun getInstructionHtml(): String =
    """
        <html>
        <body style="font-family: sans-serif; padding: 12px;">
            <h1 style="margin-top: 0;">${TodosoBundle.message("instruction.welcome.title")}</h1>
            <p>${TodosoBundle.message("instruction.empty.desc")}</p>
            <ul>
                <li>${TodosoBundle.message("instruction.step.open.file")}</li>
                <li>${TodosoBundle.message("instruction.step.submit")}</li>
                <li>${TodosoBundle.message("instruction.step.context")}</li>
                <li>
                    ${TodosoBundle.message("instruction.step.format")}<br/>
                    <code style="background-color: rgba(128,128,128,0.2); padding: 2px 4px; border-radius: 3px;">[H] task description #feature #development #v0.0.1</code>
                </li>
            </ul>
            <p>${TodosoBundle.message("instruction.doc.link", GITHUB_REPO_URL)}</p>
        </body>
        </html>
    """
      .trimIndent()
}

package com.connecthid.intellij.connection.sftp

/**
 * Class that holds information about matches of a search pattern in a specific file.
 * A single file can contain multiple occurrences of the search pattern.
 *
 * @param file The SftpFile where the matches were found
 * @param matches List of match information containing line numbers and text offsets
 */
class SftpFileOccurrence(
    val file: SftpFile,
    val matches: List<SftpMatchInfo>
) {
    // Additional methods can be added as needed
}

/**
 * Class that contains information about a specific occurrence of a search pattern.
 *
 * @param lineNumber The 1-based line number where the match was found
 * @param startOffset The start offset within the line where the match begins
 * @param endOffset The end offset within the line where the match ends
 * @param lineContent The content of the entire line containing the match
 */
class SftpMatchInfo(
    val lineNumber: Int,
    val startOffset: Int,
    val endOffset: Int,
    val lineContent: String
) {
    /**
     * Gets the matched text from the line content
     */
    val matchedText: String
        get() = lineContent.substring(startOffset, endOffset)
}

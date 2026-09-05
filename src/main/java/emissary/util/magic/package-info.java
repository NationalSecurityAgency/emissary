/**
 * Tools for identifying file types using standard configuration rules.
 *
 * <p>
 * This package provides a Java-based reader and evaluator for file-type rules (inspired by standard UNIX magic files).
 * {@link MagicNumberFactory} converts configuration lines into ready-to-use rule objects, {@link MagicMath} handles the
 * math conversions, and {@link emissary.util.MagicNumberUtil}/{@link emissary.util.UnixFile} check actual files against
 * these rules.
 * </p>
 *
 * <p>
 * This parser is designed to handle a specific set of rules from standard file identification databases, though it
 * doesn't support every single advanced feature available in modern UNIX systems. Below is an overview of how the rules
 * are structured, what features are supported, and how the parser behaves.
 * </p>
 *
 * <h2>Rule Structure</h2>
 *
 * <pre>
 * &gt;&gt;&amp;0x1E       belong&amp;0xfe00f0f0    &gt;0x3030    MS Windows shortcut, Version %d
 * --depth+off-- ----type---mask----   -op-value-  -------description--------
 * </pre>
 *
 * <h2>Feature Support Overview</h2>
 *
 * <table border="1">
 * <caption>Feature comparison</caption>
 * <tr>
 * <th>Feature</th>
 * <th>Standard UNIX file(1)</th>
 * <th>This Package</th>
 * <th>Example</th>
 * </tr>
 *
 * <tr>
 * <td colspan="4"><b>Column A - Location and Depth</b></td>
 * </tr>
 * <tr>
 * <td>Exact position (decimal, hex, octal)</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * <td>{@code 0x18}, {@code 26}, {@code 036}</td>
 * </tr>
 * <tr>
 * <td>Nesting depth using leading greater-than symbols ({@code >})</td>
 * <td>Yes</td>
 * <td>Yes, grouped by depth levels</td>
 * <td>{@code >>>0}</td>
 * </tr>
 * <tr>
 * <td>Relative position marker ({@code &})</td>
 * <td>Position relative to the end of the previous field</td>
 * <td><b>Marker is read then ignored</b>; treated as a fixed position from the start of the file</td>
 * <td>{@code >&4}</td>
 * </tr>
 * <tr>
 * <td>Parenthesized position ({@code (n)})</td>
 * <td>Yes</td>
 * <td>Yes, parentheses are removed</td>
 * <td>{@code >(26)}</td>
 * </tr>
 * <tr>
 * <td>Position pointing to another value</td>
 * <td>Looks up the stored value to find the new position</td>
 * <td><b>Rejected at load time</b>; sub-rules are dropped if errors are ignored</td>
 * <td>{@code (0x3c.l)}</td>
 * </tr>
 * <tr>
 * <td>Named positions and reusable blocks</td>
 * <td>Yes</td>
 * <td>No</td>
 * <td>{@code name zipcd}</td>
 * </tr>
 *
 * <tr>
 * <td colspan="4"><b>Column B - Data Types</b></td>
 * </tr>
 * <tr>
 * <td>Basic types (byte, short, long, text)</td>
 * <td>Yes</td>
 * <td>Yes</td>
 * <td>{@code string PK\003\004}</td>
 * </tr>
 * <tr>
 * <td>Byte order variants (big-endian and little-endian)</td>
 * <td>Yes</td>
 * <td>Yes, limited to four standard variations</td>
 * <td>{@code beshort belong leshort lelong}</td>
 * </tr>
 * <tr>
 * <td>Unsigned number types</td>
 * <td>Yes</td>
 * <td><b>Rejected</b> ("Signed Data Types unsupported"); values are treated as unsigned anyway</td>
 * <td>{@code ubelong&0x000f0000}</td>
 * </tr>
 * <tr>
 * <td>Date and time types</td>
 * <td>Yes</td>
 * <td><b>Rejected</b></td>
 * <td>{@code bedate-0x7C25B080}</td>
 * </tr>
 * <tr>
 * <td>Large numbers and decimals (quad, float, double)</td>
 * <td>Yes</td>
 * <td><b>Rejected</b></td>
 * <td>{@code lequad x}</td>
 * </tr>
 * <tr>
 * <td>Specialized types (guid, clear, indirect, etc.)</td>
 * <td>Yes</td>
 * <td><b>Rejected</b></td>
 * <td>{@code default x}</td>
 * </tr>
 * <tr>
 * <td>Bounded text search ({@code search/N})</td>
 * <td>Searches up to N bytes</td>
 * <td><b>Rejected</b></td>
 * <td>{@code search/256 self}</td>
 * </tr>
 * <tr>
 * <td>Regular expressions ({@code regex})</td>
 * <td>Searches lines using patterns</td>
 * <td><b>Rejected</b></td>
 * <td>{@code regex/c =^[\ \t]{0,10}say\ ['"]}</td>
 * </tr>
 * <tr>
 * <td>Text modifiers (case-insensitivity, whitespace rules)</td>
 * <td>Yes</td>
 * <td><b>Ignored</b>; treated as plain text</td>
 * <td>{@code string/W}, {@code string/cW}</td>
 * </tr>
 * <tr>
 * <td>Bit masks ({@code type&mask})</td>
 * <td>Yes</td>
 * <td>Yes, applied before checking numbers</td>
 * <td>{@code belong&0xfe00f0f0 >0x3030}</td>
 * </tr>
 *
 * <tr>
 * <td colspan="4"><b>Column C - Comparisons and Values</b></td>
 * </tr>
 * <tr>
 * <td>Standard values</td>
 * <td>Checks for exact equality</td>
 * <td>Checks for exact equality</td>
 * <td>{@code 0xcafebabe}</td>
 * </tr>
 * <tr>
 * <td>Comparison signs ({@code = ! > < >= <=})</td>
 * <td>Signed comparisons</td>
 * <td>Unsigned comparisons</td>
 * <td>{@code beshort >=0x0301}</td>
 * </tr>
 * <tr>
 * <td>Wildcard ({@code x})</td>
 * <td>Always matches, allows pulling values into text</td>
 * <td>Same behavior</td>
 * <td>{@code beshort x version %d}</td>
 * </tr>
 * <tr>
 * <td>Bitwise AND ({@code &})</td>
 * <td>Checks if all specified bits are set</td>
 * <td><b>Divergence:</b> Handled the same as a simple equals check</td>
 * <td>{@code leshort &0x8000}</td>
 * </tr>
 * <tr>
 * <td>Bitwise NOT ({@code ^})</td>
 * <td>Checks if bits are clear</td>
 * <td><b>Divergence:</b> Handled the same as an inequality check</td>
 * <td>{@code belong ^0x0A0D1A00}</td>
 * </tr>
 * <tr>
 * <td>Negative numbers</td>
 * <td>Yes</td>
 * <td>No, treated as raw bit patterns</td>
 * <td>{@code byte >-2}</td>
 * </tr>
 * <tr>
 * <td>Math operations on data</td>
 * <td>Yes</td>
 * <td>No</td>
 * <td>{@code byte*2^8}</td>
 * </tr>
 *
 * <tr>
 * <td colspan="4"><b>Column D - Descriptions and Output</b></td>
 * </tr>
 * <tr>
 * <td>Numeric placeholders ({@code %d %ld})</td>
 * <td>Standard formatting</td>
 * <td>Inserts the decimal value of the read bytes</td>
 * <td>{@code version %d.%ld}</td>
 * </tr>
 * <tr>
 * <td>Text placeholders ({@code %s %c})</td>
 * <td>Text and character output</td>
 * <td>Raw bytes; note that text rules always grab a single byte regardless of match size</td>
 * <td>{@code '%s'}</td>
 * </tr>
 * <tr>
 * <td>Formatting options (padding, alignment)</td>
 * <td>Yes</td>
 * <td>No</td>
 * <td>{@code %-12.12s}</td>
 * </tr>
 * <tr>
 * <td>Backspace characters ({@code \b})</td>
 * <td>Joins multiple matches</td>
 * <td>Deletes the previous character in the output text</td>
 * <td>{@code \b%d}</td>
 * </tr>
 * <tr>
 * <td>Multiple matches</td>
 * <td>Combines all matches together</td>
 * <td><b>Only the first match is used</b>, based on file order</td>
 * <td>-</td>
 * </tr>
 * </table>
 *
 * <h2>Important Behavioral Differences</h2>
 *
 * <ol>
 * <li><b>Sub-rule evaluation.</b> Standard tools test sub-rules only if their direct parent matches. Here, sub-rules
 * are grouped into depth levels: a whole layer is tested if <i>any</i> rule in the previous layer matched. This means
 * related branches can sometimes trigger together, stopping only when an entire layer fails to match.</li>
 * <li><b>Sorting.</b> Rules are evaluated strictly in the order they appear in the file, rather than being sorted by
 * importance.</li>
 * <li><b>Fallback checks.</b> If no specific rule matches a file, the system checks for low-value control bytes to
 * classify the file as either a binary file or plain text, rather than performing detailed language or character set
 * analysis.</li>
 * <li><b>Flexible formatting.</b> Extra spaces, tabs, and common syntax issues in the configuration files are
 * automatically cleaned up to handle minor formatting inconsistencies smoothly.</li>
 * <li><b>Error tolerance.</b> When error-swallowing mode is turned on, unsupported rule types are quietly skipped
 * instead of stopping the application.</li>
 * </ol>
 *
 * <h2>Examples</h2>
 *
 * <pre>
 * Supported:
 *   0   string    PK\003\004              Zip archive data
 *   &gt;4   beshort  x                       \b, version %d        - pulls value into description text
 *   0   belong&amp;0xffffff00        0xffd8ff00      JPEG image data          - uses bit masks for checks
 *   &gt;0   byte&amp;0x1F                0x07            \b, LZMA compressed      - checks masked nibble values
 *
 * Skipped when error handling is enabled:
 *   0   ubelong  &amp;0x000f0000     0x000e0000      Mach-O universal binary    - unsigned numbers
 *   &gt;&amp;32 search/256 self                \b, contains embedded zip   - bounded text search
 *   0   regex   ^#!.*python    Python script               - regular expressions
 *   0   lequad  x                       Little-endian 64-bit        - large 8-byte numbers
 *
 * Parsed with minor behavioral differences:
 *   &gt;(0x3c.l)  lelong  x   \b resource fork           - indirect position: rule is dropped entirely
 *   &gt;&amp;4        beshort x   \b, next header            - relative marker ignored: checked at absolute position 4
 *   0  string/W  ABCDEF   whitespace tolerant match   - modifier ignored: requires exact text spacing
 * </pre>
 */
package emissary.util.magic;

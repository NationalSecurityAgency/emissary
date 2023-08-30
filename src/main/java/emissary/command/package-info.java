/**
 * Provides classes necessary to interact with Emissary on the command line.
 * <p>
 * {@link emissary.command.EmissaryCommand}s are used by {@link emissary.Emissary} to handle different functions of the
 * system, which include starting, stopping, pausing, and querying the system. {@link emissary.command.EmissaryCommand}s
 * wrap <a href="https://picocli.info/">Picocli</a> classes for parsing the command line,
 * converting/validating/assigning the fields with the correct values, and usage printing.
 *
 * @see <a href="https://picocli.info/">Picocli</a>
 */
package emissary.command;

/**
 * The Emissary Directory controls the advertisement and availability of services (places) in the Emissary system. The
 * directory tracks cost and quality of all places that register and propagates that information throughout the system.
 * The Directory is a specialized implementation of the {@link emissary.place.ServiceProviderPlace}, so it can advertise
 * itself as well.
 * <p>
 * The Directories are connected in a P2P network that utilizes both Rendezvous and Relay concepts. A static
 * configuration file of initial rendezvous directories is read and these directories are monitored and contact is
 * attempted. The current directory attempts both a zone transfer from each rendezvous directory and to register itself
 * as a peer with the rendezvous. Upon successful zone transfer (see {@link emissary.directory.DirectoryXmlContainer}
 * for the zone transfer format), any foreign keys are evaluated to see if they are also peers of the current directory.
 * If so, they are tracked, added to the rendezvous peer list and zone transfers from them are attempted in like manner.
 * In this way all of the directories in a peer group find each other by the use of one or more statically configured
 * rendezvous hosts.
 * <p>
 * Directory relay hosts are directories through which other peer groups are forced to communicate due to policy or
 * network topology. If a system has a configured relay directory, they will be connected to it in a parent-child
 * relationship. The parent will proxy keys from itself and its peer group to the child group and will proxy keys from
 * the child group to its peer group. This forces all communications to come through the directory acting as a proxy.
 * The forwarding from the proxy key to the real key takes place when the message arrives on the process or
 * processHeavyDuty method.
 * <p>
 * Directories are observable for several behaviours. The {@link emissary.directory.PeerObserver} will, when registered,
 * get notice of updates to the peer group list. Any time a peer joins or leaves the group the updated list of peers
 * will be sent to the observer. The {@link emissary.directory.PlaceObserver} registers with a pattern that receives
 * notice whenever a matching key is added, removed, or has its cost changed. See
 * {@link emissary.directory.KeyManipulator#gmatch} for details on the patterns, but in general the normal four-tuple
 * keys can be wildcarded. The default pattern, *.*.*.* will match every directory entry. The
 * {@link emissary.directory.DirectoryObserverManager} is the mechanism used in the DirectoryPlace to manage
 * communication with all of the registered observers.
 */
package emissary.directory;

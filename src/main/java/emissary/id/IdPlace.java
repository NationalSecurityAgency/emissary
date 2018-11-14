package emissary.id;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import emissary.config.Configurator;
import emissary.core.Form;
import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

/**
 * Abstract class that is the parent of all places that operate in the ID phase of the workflow.
 */
public abstract class IdPlace extends ServiceProviderPlace {
    /** Set of final id forms that do not add unknown on top */
    protected Set<String> finalForms;

    /* Forms to rename */
    protected Map<String, String> renames;

    /* Forms to ignore */
    protected Set<String> ignores;

    /**
     * Create and register an id place with all default config information
     */
    public IdPlace() throws IOException {
        super();
        configureIdPlace();
    }

    /**
     * Create an Id Place and register it at the location specified
     * 
     * @param placeLoc the location for registering this place
     */
    public IdPlace(final String placeLoc) throws IOException {
        super(placeLoc);
        configureIdPlace();
    }

    /**
     * Create and register constructor, called from the place by super(x,y,z)
     * 
     * @param configFile the config location file or resource
     * @param theDir controlling directory
     * @param thePlaceLocation key to use in registration
     */
    protected IdPlace(final String configFile, final String theDir, final String thePlaceLocation) throws IOException {
        super(configFile, theDir, thePlaceLocation);
        configureIdPlace();
    }

    /**
     * Create and register with default directory
     * 
     * @param configFile the config location file or resource
     * @param placeLocation key to use in registration
     */
    public IdPlace(final String configFile, final String placeLocation) throws IOException {
        super(configFile, placeLocation);
        configureIdPlace();
    }

    /**
     * Create an id place with config data from a stream
     * 
     * @param configStream stream of config data
     * @param theDir string name of our directory
     * @param thePlaceLocation string name of our location
     */
    protected IdPlace(final InputStream configStream, final String theDir, final String thePlaceLocation) throws IOException {
        super(configStream, theDir, thePlaceLocation);
    }

    /**
     * Construct with config data from a stream on the local directory
     * 
     * @param configStream stream of config data
     */
    protected IdPlace(final InputStream configStream) throws IOException {
        super(configStream);
    }

    /**
     * Save a list of all the forms that this place is a service proxy for and a list of final id determinations that this
     * place can make so that any new form being set can be differentiated as final or non-final and the stacks cleaned up
     * appropriately
     * <ul>
     * <li>FINAL_ID - current form values that do not get UNKNOWN pushed on top</li>
     * <li>ID_RENAME_ - current form values to rename</li>
     * <li>ID_IGNORE - current form values to ignore</li>
     * </ul>
     */
    public void configureIdPlace() {
        // FINAL_ID should be a subset of SERVICE_PROXY
        this.finalForms = configG.findEntriesAsSet("FINAL_ID");
        this.renames = configG.findStringMatchMap("ID_RENAME_", Configurator.PRESERVE_CASE);
        this.ignores = configG.findEntriesAsSet("ID_IGNORE");
    }

    /**
     * Before setting a non-final current form, pop everything this place is s proxy for, then push the new form onto the
     * currentForm() stack, then push UNKNOWN on right after it (unless the newForm itself is UNKNOWN).
     */
    public int setNonFinalCurrentForm(final IBaseDataObject d, final String newForm) {

        if (this.ignores.contains(newForm)) {
            return d.currentFormSize();
        }

        final Set<String> serviceProxies = getProxies();
        while (serviceProxies.contains(d.currentForm()) || d.currentForm().equals(newForm)) {
            d.popCurrentForm();
        }

        int sz = 0;

        if (newForm != null) {
            sz = d.pushCurrentForm(renamedForm(newForm));
        }

        if (!Form.UNKNOWN.equals(newForm)) {
            sz = d.pushCurrentForm(Form.UNKNOWN);
        }

        return sz;
    }

    /**
     * Return the form renamed if it is listed as a RENAME_ID otherwise just return as-id
     * 
     * @param form the form to check
     * @return the renamed form
     */
    protected String renamedForm(final String form) {
        if (this.renames.containsKey(form)) {
            return this.renames.get(form);
        } else {
            return form;
        }
    }

    /**
     * Before setting a final current form, pop everything this place is a proxy for, then push the new form onto both the
     * currentForm() and destination() stack.
     */
    public int setFinalCurrentForm(final IBaseDataObject d, final String newForm) {

        if (this.ignores.contains(newForm)) {
            return d.currentFormSize();
        }

        final Set<String> serviceProxies = getProxies();
        final String nf = renamedForm(newForm);
        while (serviceProxies.contains(d.currentForm()) || d.currentForm().equals(nf)) {
            d.popCurrentForm();
        }

        return d.pushCurrentForm(nf);
    }


    /**
     * Set the current form after deciding if it's a FINAL_ID or not
     */
    public int setCurrentForm(final IBaseDataObject d, final String newForm) {

        if (this.ignores.contains(newForm)) {
            return d.currentFormSize();
        }

        final int sz;
        if (newForm != null && (this.finalForms.contains(newForm) || this.finalForms.contains("*"))) {
            sz = setFinalCurrentForm(d, newForm);
        } else {
            sz = setNonFinalCurrentForm(d, newForm);
        }

        return sz;
    }

    /**
     * Set a whole bunch of new forms. The top one may or may not be a FINAL_ID, all others are FINAL_ID de facto so that
     * extraneous UNKNOWNs are not put on the stack
     */
    public int setCurrentForm(final IBaseDataObject d, final Collection<String> newForms) {

        final int sz;
        if (newForms == null || newForms.size() < 1) {
            sz = setNonFinalCurrentForm(d, null);
        } else {
            final String[] forms = newForms.toArray(new String[0]);
            // Set all the but top dog as final
            for (int i = (forms.length - 1); i > 0; i--) {
                setFinalCurrentForm(d, forms[i]);
            }

            // Only decide between final/non-final on the top dog
            sz = setCurrentForm(d, forms[0]);
        }

        return sz;
    }
}

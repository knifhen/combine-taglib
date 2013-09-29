package se.intem.web.taglib.combined.tags;

import com.google.common.base.Optional;

import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyTagSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.intem.web.taglib.combined.node.ConfigurationItem;

public abstract class ConfigurationItemAwareTagSupport extends BodyTagSupport {

    private CombineJsonConfiguration json;

    private DependencyCache dependencyCache;

    public static final String REQUEST_CONFIGURATION_ITEMS_KEY = "combine_configuration_items";

    /** Logger for this class. */
    private static final Logger log = LoggerFactory.getLogger(ConfigurationItemAwareTagSupport.class);

    public ConfigurationItemAwareTagSupport() {
        this.dependencyCache = DependencyCache.get();
    }

    @Override
    public void setPageContext(final PageContext pageContext) {
        super.setPageContext(pageContext);

        /* Lazily create config */
        if (this.json == null) {
            this.json = CombineJsonConfiguration.get().withServletContext(pageContext.getServletContext());
        }
    }

    public ConfigurationItemsCollection getConfigurationItems() {
        ConfigurationItemsCollection collection = (ConfigurationItemsCollection) pageContext.getRequest().getAttribute(
                REQUEST_CONFIGURATION_ITEMS_KEY);

        if (collection == null) {
            log.debug("Creating new list");

            Optional<ConfigurationItemsCollection> parent = json.readConfiguration();
            if (parent.isPresent()) {
                /* Cannot call get on absent, so use only if present. */
                collection = new ConfigurationItemsCollection(parent.get());
            } else {
                collection = new ConfigurationItemsCollection();
            }

            /* Read dependencies for resources defined in configuration file. */
            for (ConfigurationItem ci : collection) {
                dependencyCache.readDependenciesFromResources(pageContext.getServletContext(), ci);
            }

            pageContext.getRequest().setAttribute(REQUEST_CONFIGURATION_ITEMS_KEY, collection);
        }

        return collection;
    }
}

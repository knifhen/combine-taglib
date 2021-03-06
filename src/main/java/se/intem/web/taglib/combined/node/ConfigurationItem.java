package se.intem.web.taglib.combined.node;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import se.intem.web.taglib.combined.RequestPath;
import se.intem.web.taglib.combined.ResourceType;
import se.intem.web.taglib.combined.configuration.ManagedResource;
import se.intem.web.taglib.combined.configuration.ServerPathToManagedResource;
import se.intem.web.taglib.combined.configuration.SupportsConditional;
import se.intem.web.taglib.combined.servlet.CombinedConfigurationHolder;

/**
 * Limitation: ConfigurationItem can contain only either remote or local resources, not both. If they contain both, then
 * no combining will be made on any resources.
 */
public class ConfigurationItem implements SupportsConditional {

    private String name;
    private boolean reloadable = true;
    private boolean library = false;
    private boolean combine = true;

    /* Requires that were set on group */
    private LinkedHashSet<String> requires = Sets.newLinkedHashSet();

    /* Requires that were parsed from contents needs to be tracked separately so that it can be replaced on changes. */
    private LinkedHashSet<String> parsedRequires = Sets.newLinkedHashSet();

    private LinkedHashSet<String> optional = Sets.newLinkedHashSet();

    private List<RequestPath> js = Lists.newArrayList();
    private List<RequestPath> css = Lists.newArrayList();

    private boolean supportsDevMode;

    private Map<String, String> replace = Maps.newLinkedHashMap();

    /**
     * True to generate an id to be used with dynamic css libraries such as YUI Stylesheet.
     */
    private boolean supportsDynamicCss;

    /**
     * IE conditional comment, without if, such as "IE lt 10"
     */
    private String conditional;

    /*
     * This item represents a root of the tree, such as a requires or inline tag or a page library; no other depends on
     * this node.
     */
    private boolean root;

    public Iterable<String> getRequires() {
        return Iterables.concat(requires, parsedRequires);
    }

    @VisibleForTesting
    List<String> getRequiresList() {
        return FluentIterable.from(getRequires()).toList();
    }

    public void setRequires(final List<String> requires) {
        for (String require : requires) {
            addRequires(require);
        }
    }

    public List<RequestPath> getJs() {

        if (replace.isEmpty()) {
            return js;
        }

        return FluentIterable.from(js).transform(new ResolvePlaceholders(replace)).toList();
    }

    public void setJs(final List<RequestPath> js) {
        this.js = js;
    }

    public List<RequestPath> getCss() {

        if (replace.isEmpty()) {
            return css;
        }

        return FluentIterable.from(css).transform(new ResolvePlaceholders(replace)).toList();
    }

    public void setCss(final List<RequestPath> css) {
        this.css = css;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isReloadable() {
        return reloadable;
    }

    public boolean isRemote() {
        Optional<RequestPath> remoteJs = FluentIterable.from(js).firstMatch(RequestPath.isRemote);
        Optional<RequestPath> remoteCss = FluentIterable.from(css).firstMatch(RequestPath.isRemote);
        return remoteJs.isPresent() || remoteCss.isPresent();
    }

    public boolean isAllRemote() {
        Optional<RequestPath> localJs = FluentIterable.from(js).firstMatch(Predicates.not(RequestPath.isRemote));
        Optional<RequestPath> localCss = FluentIterable.from(css).firstMatch(Predicates.not(RequestPath.isRemote));
        return !localJs.isPresent() && !localCss.isPresent();
    }

    public void setReloadable(final boolean reloadable) {
        this.reloadable = reloadable;
    }

    public boolean isLibrary() {
        return library;
    }

    public void setLibrary(final boolean library) {
        this.library = library;
    }

    public void addRequires(final String requires) {
        Iterable<String> split = Splitter.on(CharMatcher.anyOf(" ,")).trimResults().omitEmptyStrings()
                .split(Strings.nullToEmpty(requires));
        this.requires.addAll(FluentIterable.from(split).toList());
    }

    public boolean isCombine() {
        return combine;
    }

    public void setCombine(final boolean combine) {
        this.combine = combine;
    }

    public void setSupportsDevMode(final boolean supportsDevMode) {
        this.supportsDevMode = supportsDevMode;
    }

    public boolean isSupportsDevMode() {
        return supportsDevMode;
    }

    @Override
    public String toString() {
        return Strings.nullToEmpty(name);
    }

    public void addRequires(final Iterable<String> requires) {
        for (String require : requires) {
            addRequires(require);
        }
    }

    public List<RequestPath> getPaths(final ResourceType type) {
        if (ResourceType.js.equals(type)) {
            return getJs();
        }

        return getCss();
    }

    public boolean hasResources(final ResourceType type) {
        return !getPaths(type).isEmpty();
    }

    /**
     * An empty group contains no members but it may still have dependencies. Typically a requires tag.
     */
    public boolean isEmpty() {
        ResourceType[] values = ResourceType.values();
        for (ResourceType type : values) {
            if (hasResources(type)) {
                return false;
            }
        }

        return true;
    }

    public Map<ResourceType, List<ManagedResource>> getRealPaths(final ServletContext servletContext) {
        Map<ResourceType, List<ManagedResource>> result = Maps.newHashMap();

        ResourceType[] values = ResourceType.values();
        for (ResourceType resourceType : values) {
            List<ManagedResource> realPaths = FluentIterable.from(getPaths(resourceType))
                    .transform(new ServerPathToManagedResource(servletContext, true)).toList();
            if (!realPaths.isEmpty()) {
                result.put(resourceType, realPaths);
            }
        }

        return result;
    }

    public boolean shouldBeCombined() {
        if (isAllRemote()) {
            return false;
        }

        if (!isCombine()) {
            return false;
        }

        boolean outputAsIs = CombinedConfigurationHolder.isDevMode() && isSupportsDevMode();
        return !outputAsIs;
    }

    public long getLastChange(final ServletContext servletContext) {
        Map<ResourceType, List<ManagedResource>> map = getRealPaths(servletContext);
        Iterable<ManagedResource> realPaths = Iterables.concat(map.values());

        long result = 0;
        for (ManagedResource managedResource : realPaths) {
            if (!managedResource.isTimestampSupported()) {
                continue;
            }

            result = Math.max(result, managedResource.lastModified());
        }

        return result;
    }

    public List<String> getOptional() {
        return Lists.newArrayList(optional);
    }

    public void setOptional(final List<String> optionals) {
        for (String optional : optionals) {
            addOptional(optional);
        }
    }

    /**
     * Add optional dependency. An optional dependency would not normally be included, but if some other resource
     * depends on it, then this resource will use it (i.e. include it before this resource).
     *
     * For example: angular optionally requires jquery. Angular will use jquery if included, but jquery is not required.
     * However if jquery is included, then it must be loaded before angular.
     */
    public void addOptional(final String optional) {
        Iterable<String> split = Splitter.on(CharMatcher.anyOf(" ,")).trimResults().omitEmptyStrings()
                .split(Strings.nullToEmpty(optional));
        this.optional.addAll(FluentIterable.from(split).toList());
    }

    public void replaceParsedRequires(final Iterable<String> requires) {
        this.parsedRequires = Sets.newLinkedHashSet();
        for (String string : requires) {
            addParsedRequires(string);
        }
    }

    private void addParsedRequires(final String string) {
        Iterable<String> split = Splitter.on(CharMatcher.anyOf(" ,")).trimResults().omitEmptyStrings()
                .split(Strings.nullToEmpty(string));
        this.parsedRequires.addAll(FluentIterable.from(split).toList());

    }

    public boolean hasDependencies() {
        return !requires.isEmpty() || !parsedRequires.isEmpty() || !optional.isEmpty();
    }

    public int getSize() {
        return js.size() + css.size();
    }

    public String getConditional() {
        return conditional;
    }

    public void setConditional(final String conditional) {
        this.conditional = Strings.nullToEmpty(conditional).replace("if ", "");
    }

    public boolean hasConditional() {
        return !Strings.nullToEmpty(this.conditional).trim().isEmpty();
    }

    public boolean isSupportsDynamicCss() {
        return supportsDynamicCss;
    }

    public void setSupportsDynamicCss(final boolean supportsDynamicCss) {
        this.supportsDynamicCss = supportsDynamicCss;
    }

    public void setRoot(final boolean root) {
        this.root = root;
    }

    public boolean isRoot() {
        return root;
    }

    public Map<String, String> getReplace() {
        return replace;
    }

    public void setReplace(final Map<String, String> replace) {
        this.replace = replace;
    }
}

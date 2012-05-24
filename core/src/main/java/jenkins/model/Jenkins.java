/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe,
 * Stephen Connolly, Tom Huybrechts, Yahoo! Inc., Alan Harder, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.model;


import hudson.model.LoadStatistics;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.AbstractCIBase;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AdministrativeMonitor;
import hudson.model.AllView;
import hudson.model.Api;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Failure;
import hudson.model.Fingerprint;
import hudson.model.FingerprintCleanupThread;
import hudson.model.FingerprintMap;
import hudson.model.FullDuplexHttpChannel;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Label;
import hudson.model.ListView;
import hudson.model.LoadBalancer;
import hudson.model.ManagementLink;
import hudson.model.ModifiableItemGroup;
import hudson.model.NoFingerprintMatch;
import hudson.model.Node.Mode;
import hudson.model.OverallLoadStatistics;
import hudson.model.Project;
import hudson.model.RestartListener;
import hudson.model.RootAction;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.UnprotectedRootAction;
import hudson.model.UpdateCenter;
import hudson.model.User;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.model.Descriptor.FormException;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SCMListener;
import hudson.model.listeners.SaveableListener;
import hudson.model.Queue;
import hudson.model.WorkspaceCleanupThread;

import antlr.ANTLRException;
import com.google.common.collect.ImmutableMap;
import com.thoughtworks.xstream.XStream;
import hudson.BulkChange;
import hudson.DNSMultiCast;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.LocalPluginManager;
import hudson.Lookup;
import hudson.markup.MarkupFormatter;
import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.StructuredForm;
import hudson.TcpSlaveAgentListener;
import hudson.UDPBroadcastThread;
import hudson.Util;
import static hudson.Util.fixEmpty;
import static hudson.Util.fixNull;
import hudson.WebAppMain;
import hudson.XmlFile;
import hudson.cli.CLICommand;
import hudson.cli.CliEntryPoint;
import hudson.cli.CliManagerImpl;
import hudson.cli.declarative.CLIMethod;
import hudson.cli.declarative.CLIResolver;
import hudson.init.InitMilestone;
import hudson.init.InitReactorListener;
import hudson.init.InitStrategy;
import hudson.lifecycle.Lifecycle;
import hudson.logging.LogRecorderManager;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.markup.RawHtmlMarkupFormatter;
import hudson.remoting.Channel;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.AuthorizationStrategy;
import hudson.security.BasicAuthenticationFilter;
import hudson.security.FederatedLoginService;
import hudson.security.HudsonFilter;
import hudson.security.LegacyAuthorizationStrategy;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.security.SecurityMode;
import hudson.security.SecurityRealm;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeList;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Mailer;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.AdministrativeError;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.ClockDifference;
import hudson.util.CopyOnWriteList;
import hudson.util.CopyOnWriteMap;
import hudson.util.DaemonThreadFactory;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.Futures;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import hudson.util.Iterators;
import hudson.util.Memoizer;
import hudson.util.MultipartFormDataParser;
import hudson.util.RemotingDiagnostics;
import hudson.util.RemotingDiagnostics.HeapDump;
import hudson.util.StreamTaskListener;
import hudson.util.TextFile;
import hudson.util.VersionNumber;
import hudson.util.XStream2;
import hudson.util.Service;
import hudson.views.DefaultMyViewsTabBar;
import hudson.views.DefaultViewsTabBar;
import hudson.views.MyViewsTabBar;
import hudson.views.ViewsTabBar;
import hudson.widgets.Widget;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.ui.AbstractProcessingFilter;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.Script;
import org.apache.commons.logging.LogFactory;
import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorListener;
import org.jvnet.hudson.reactor.TaskGraphBuilder.Handle;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.framework.adjunct.AdjunctManager;
import org.kohsuke.stapler.jelly.JellyClassLoaderTearOff;
import org.kohsuke.stapler.jelly.JellyRequestDispatcher;
import org.xml.sax.InputSource;

import javax.crypto.SecretKey;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import static hudson.init.InitMilestone.*;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.text.Collator;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Root object of the system.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public class Jenkins extends AbstractCIBase implements ModifiableItemGroup<TopLevelItem>, StaplerProxy, StaplerFallback, ViewGroup, AccessControlled, DescriptorByNameOwner {
    private transient final Queue queue;

    /**
     * Stores various objects scoped to {@link Jenkins}.
     */
    public transient final Lookup lookup = new Lookup();

    /**
     * We update this field to the current version of Hudson whenever we save {@code config.xml}.
     * This can be used to detect when an upgrade happens from one version to next.
     *
     * <p>
     * Since this field is introduced starting 1.301, "1.0" is used to represent every version
     * up to 1.300. This value may also include non-standard versions like "1.301-SNAPSHOT" or
     * "?", etc., so parsing needs to be done with a care.
     *
     * @since 1.301
     */
    // this field needs to be at the very top so that other components can look at this value even during unmarshalling
    private String version = "1.0";

    /**
     * Number of executors of the master node.
     */
    private int numExecutors = 2;

    /**
     * Job allocation strategy.
     */
    private Mode mode = Mode.NORMAL;

    /**
     * False to enable anyone to do anything.
     * Left as a field so that we can still read old data that uses this flag.
     *
     * @see #authorizationStrategy
     * @see #securityRealm
     */
    private Boolean useSecurity;

    /**
     * Controls how the
     * <a href="http://en.wikipedia.org/wiki/Authorization">authorization</a>
     * is handled in Hudson.
     * <p>
     * This ultimately controls who has access to what.
     *
     * Never null.
     */
    private volatile AuthorizationStrategy authorizationStrategy = AuthorizationStrategy.UNSECURED;

    /**
     * Controls a part of the
     * <a href="http://en.wikipedia.org/wiki/Authentication">authentication</a>
     * handling in Hudson.
     * <p>
     * Intuitively, this corresponds to the user database.
     *
     * See {@link HudsonFilter} for the concrete authentication protocol.
     *
     * Never null. Always use {@link #setSecurityRealm(SecurityRealm)} to
     * update this field.
     *
     * @see #getSecurity()
     * @see #setSecurityRealm(SecurityRealm)
     */
    private volatile SecurityRealm securityRealm = SecurityRealm.NO_AUTHENTICATION;

    /**
     * Root directory for the workspaces. This value will be variable-expanded against
     * job name and JENKINS_HOME.
     *
     * @see #getWorkspaceFor(TopLevelItem)
     */
    private String workspaceDir = "${ITEM_ROOTDIR}/"+WORKSPACE_DIRNAME;

    /**
     * Root directory for the workspaces. This value will be variable-expanded against
     * job name and JENKINS_HOME.
     *
     * @see #getBuildDirFor(Job)
     */
    private String buildsDir = "${ITEM_ROOTDIR}/builds";

    /**
     * Message displayed in the top page.
     */
    private String systemMessage;

    private MarkupFormatter markupFormatter;

    /**
     * Root directory of the system.
     */
    public transient final File root;

    /**
     * Where are we in the initialization?
     */
    private transient volatile InitMilestone initLevel = InitMilestone.STARTED;

    /**
     * All {@link Item}s keyed by their {@link Item#getName() name}s.
     */
    /*package*/ transient final Map<String,TopLevelItem> items = new CopyOnWriteMap.Tree<String,TopLevelItem>(CaseInsensitiveComparator.INSTANCE);

    /**
     * The sole instance.
     */
    private static Jenkins theInstance;

    private transient volatile boolean isQuietingDown;
    private transient volatile boolean terminating;

    private List<JDK> jdks = new ArrayList<JDK>();

    private transient volatile DependencyGraph dependencyGraph;

    /**
     * Currently active Views tab bar.
     */
    private volatile ViewsTabBar viewsTabBar = new DefaultViewsTabBar();

    /**
     * Currently active My Views tab bar.
     */
    private volatile MyViewsTabBar myViewsTabBar = new DefaultMyViewsTabBar();

    /**
     * All {@link ExtensionList} keyed by their {@link ExtensionList#extensionType}.
     */
    private transient final Memoizer<Class,ExtensionList> extensionLists = new Memoizer<Class,ExtensionList>() {
        public ExtensionList compute(Class key) {
            return ExtensionList.create(Jenkins.this,key);
        }
    };

    /**
     * All {@link DescriptorExtensionList} keyed by their {@link DescriptorExtensionList#describableType}.
     */
    private transient final Memoizer<Class,DescriptorExtensionList> descriptorLists = new Memoizer<Class,DescriptorExtensionList>() {
        public DescriptorExtensionList compute(Class key) {
            return DescriptorExtensionList.createDescriptorList(Jenkins.this,key);
        }
    };

    /**
     * {@link Computer}s in this Hudson system. Read-only.
     */
    protected transient final Map<Node,Computer> computers = new CopyOnWriteMap.Hash<Node,Computer>();

    /**
     * Active {@link Cloud}s.
     */
    public final Hudson.CloudList clouds = new Hudson.CloudList(this);

    public static class CloudList extends DescribableList<Cloud,Descriptor<Cloud>> {
        public CloudList(Jenkins h) {
            super(h);
        }

        public CloudList() {// needed for XStream deserialization
        }

        public Cloud getByName(String name) {
            for (Cloud c : this)
                if (c.name.equals(name))
                    return c;
            return null;
        }

        @Override
        protected void onModified() throws IOException {
            super.onModified();
            Jenkins.getInstance().trimLabels();
        }
    }

    /**
     * Set of installed cluster nodes.
     * <p>
     * We use this field with copy-on-write semantics.
     * This field has mutable list (to keep the serialization look clean),
     * but it shall never be modified. Only new completely populated slave
     * list can be set here.
     * <p>
     * The field name should be really {@code nodes}, but again the backward compatibility
     * prevents us from renaming.
     */
    protected volatile NodeList slaves;

    /**
     * Quiet period.
     *
     * This is {@link Integer} so that we can initialize it to '5' for upgrading users.
     */
    /*package*/ Integer quietPeriod;

    /**
     * Global default for {@link AbstractProject#getScmCheckoutRetryCount()}
     */
    /*package*/ int scmCheckoutRetryCount;

    /**
     * {@link View}s.
     */
    private final CopyOnWriteArrayList<View> views = new CopyOnWriteArrayList<View>();

    /**
     * Name of the primary view.
     * <p>
     * Start with null, so that we can upgrade pre-1.269 data well.
     * @since 1.269
     */
    private volatile String primaryView;

    private transient final ViewGroupMixIn viewGroupMixIn = new ViewGroupMixIn(this) {
        protected List<View> views() { return views; }
        protected String primaryView() { return primaryView; }
        protected void primaryView(String name) { primaryView=name; }
    };


    private transient final FingerprintMap fingerprintMap = new FingerprintMap();

    /**
     * Loaded plugins.
     */
    public transient final PluginManager pluginManager;

    public transient volatile TcpSlaveAgentListener tcpSlaveAgentListener;

    private transient UDPBroadcastThread udpBroadcastThread;

    private transient DNSMultiCast dnsMultiCast;

    /**
     * List of registered {@link SCMListener}s.
     */
    private transient final CopyOnWriteList<SCMListener> scmListeners = new CopyOnWriteList<SCMListener>();

    /**
     * TCP slave agent port.
     * 0 for random, -1 to disable.
     */
    private int slaveAgentPort =0;

    /**
     * Whitespace-separated labels assigned to the master as a {@link Node}.
     */
    private String label="";

    /**
     * {@link hudson.security.csrf.CrumbIssuer}
     */
    private volatile CrumbIssuer crumbIssuer;

    /**
     * All labels known to Hudson. This allows us to reuse the same label instances
     * as much as possible, even though that's not a strict requirement.
     */
    private transient final ConcurrentHashMap<String,Label> labels = new ConcurrentHashMap<String,Label>();

    /**
     * Load statistics of the entire system.
     *
     * This includes every executor and every job in the system.
     */
    @Exported
    public transient final OverallLoadStatistics overallLoad = new OverallLoadStatistics();

    /**
     * Load statistics of the free roaming jobs and slaves.
     * 
     * This includes all executors on {@link Mode#NORMAL} nodes and jobs that do not have any assigned nodes.
     *
     * @since 1.467
     */
    @Exported
    public transient final LoadStatistics unlabeledLoad = new UnlabeldLoadStatistics();

    /**
     * {@link NodeProvisioner} that reacts to {@link #unlabeledLoad}.
     * @since 1.467
     */
    public transient final NodeProvisioner unlabeledNodeProvisioner = new NodeProvisioner(null,unlabeledLoad);

    /**
     * @deprecated as of 1.467
     *      Use {@link #unlabeledNodeProvisioner}.
     *      This was broken because it was tracking all the executors in the system, but it was only tracking
     *      free-roaming jobs in the queue. So {@link Cloud} fails to launch nodes when you have some exclusive
     *      slaves and free-roaming jobs in the queue.
     */
    @Restricted(NoExternalUse.class)
    public transient final NodeProvisioner overallNodeProvisioner = unlabeledNodeProvisioner;


    public transient final ServletContext servletContext;

    /**
     * Transient action list. Useful for adding navigation items to the navigation bar
     * on the left.
     */
    private transient final List<Action> actions = new CopyOnWriteArrayList<Action>();

    /**
     * List of master node properties
     */
    private DescribableList<NodeProperty<?>,NodePropertyDescriptor> nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(this);

    /**
     * List of global properties
     */
    private DescribableList<NodeProperty<?>,NodePropertyDescriptor> globalNodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(this);

    /**
     * {@link AdministrativeMonitor}s installed on this system.
     *
     * @see AdministrativeMonitor
     */
    public transient final List<AdministrativeMonitor> administrativeMonitors = getExtensionList(AdministrativeMonitor.class);

    /**
     * Widgets on Hudson.
     */
    private transient final List<Widget> widgets = getExtensionList(Widget.class);

    /**
     * {@link AdjunctManager}
     */
    private transient final AdjunctManager adjuncts;

    /**
     * Code that handles {@link ItemGroup} work.
     */
    private transient final ItemGroupMixIn itemGroupMixIn = new ItemGroupMixIn(this,this) {
        @Override
        protected void add(TopLevelItem item) {
            items.put(item.getName(),item);
        }

        @Override
        protected File getRootDirFor(String name) {
            return Jenkins.this.getRootDirFor(name);
        }

        /**
         *send the browser to the config page
         * use View to trim view/{default-view} from URL if possible
         */
        @Override
        protected String redirectAfterCreateItem(StaplerRequest req, TopLevelItem result) throws IOException {
            String redirect = result.getUrl()+"configure";
            List<Ancestor> ancestors = req.getAncestors();
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                Object o = ancestors.get(i).getObject();
                if (o instanceof View) {
                    redirect = req.getContextPath() + '/' + ((View)o).getUrl() + redirect;
                    break;
                }
            }
            return redirect;
        }
    };

    @CLIResolver
    public static Jenkins getInstance() {
        return theInstance;
    }

    /**
     * Secrete key generated once and used for a long time, beyond
     * container start/stop. Persisted outside <tt>config.xml</tt> to avoid
     * accidental exposure.
     */
    private transient final String secretKey;

    private transient final UpdateCenter updateCenter = new UpdateCenter();

    /**
     * True if the user opted out from the statistics tracking. We'll never send anything if this is true.
     */
    private Boolean noUsageStatistics;

    /**
     * HTTP proxy configuration.
     */
    public transient volatile ProxyConfiguration proxy;

    /**
     * Bound to "/log".
     */
    private transient final LogRecorderManager log = new LogRecorderManager();

    protected Jenkins(File root, ServletContext context) throws IOException, InterruptedException, ReactorException {
        this(root,context,null);
    }

    /**
     * @param pluginManager
     *      If non-null, use existing plugin manager.  create a new one.
     */
    protected Jenkins(File root, ServletContext context, PluginManager pluginManager) throws IOException, InterruptedException, ReactorException {
    	// As hudson is starting, grant this process full control
    	SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            this.root = root;
            this.servletContext = context;
            computeVersion(context);
            if(theInstance!=null)
                throw new IllegalStateException("second instance");
            theInstance = this;

            if (!new File(root,"jobs").exists()) {
                // if this is a fresh install, use more modern default layout that's consistent with slaves
                workspaceDir = "${JENKINS_HOME}/workspace/${ITEM_FULLNAME}";
            }

            // doing this early allows InitStrategy to set environment upfront
            final InitStrategy is = InitStrategy.get(Thread.currentThread().getContextClassLoader());

            Trigger.timer = new Timer("Jenkins cron thread");
            queue = new Queue(CONSISTENT_HASH?LoadBalancer.CONSISTENT_HASH:LoadBalancer.DEFAULT);

            try {
                dependencyGraph = DependencyGraph.EMPTY;
            } catch (InternalError e) {
                if(e.getMessage().contains("window server")) {
                    throw new Error("Looks like the server runs without X. Please specify -Djava.awt.headless=true as JVM option",e);
                }
                throw e;
            }

            // get or create the secret
            TextFile secretFile = new TextFile(new File(getRootDir(),"secret.key"));
            if(secretFile.exists()) {
                secretKey = secretFile.readTrim();
            } else {
                SecureRandom sr = new SecureRandom();
                byte[] random = new byte[32];
                sr.nextBytes(random);
                secretKey = Util.toHexString(random);
                secretFile.write(secretKey);
            }

            try {
                proxy = ProxyConfiguration.load();
            } catch (IOException e) {
                LOGGER.log(SEVERE, "Failed to load proxy configuration", e);
            }

            if (pluginManager==null)
                pluginManager = new LocalPluginManager(this);
            this.pluginManager = pluginManager;
            // JSON binding needs to be able to see all the classes from all the plugins
            WebApp.get(servletContext).setClassLoader(pluginManager.uberClassLoader);

            adjuncts = new AdjunctManager(servletContext, pluginManager.uberClassLoader,"adjuncts/"+VERSION_HASH);

            // initialization consists of ...
            executeReactor( is,
                    pluginManager.initTasks(is),    // loading and preparing plugins
                    loadTasks(),                    // load jobs
                    InitMilestone.ordering()        // forced ordering among key milestones
            );

            if(KILL_AFTER_LOAD)
                System.exit(0);

            if(slaveAgentPort!=-1) {
                try {
                    tcpSlaveAgentListener = new TcpSlaveAgentListener(slaveAgentPort);
                } catch (BindException e) {
                    new AdministrativeError(getClass().getName()+".tcpBind",
                            "Failed to listen to incoming slave connection",
                            "Failed to listen to incoming slave connection. <a href='configure'>Change the port number</a> to solve the problem.",e);
                }
            } else
                tcpSlaveAgentListener = null;

            try {
                udpBroadcastThread = new UDPBroadcastThread(this);
                udpBroadcastThread.start();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Faild to broadcast over UDP",e);
            }
            dnsMultiCast = new DNSMultiCast(this);

            updateComputerList();

            {// master is online now
                Computer c = toComputer();
                if(c!=null)
                    for (ComputerListener cl : ComputerListener.all())
                        cl.onOnline(c,StreamTaskListener.fromStdout());
            }

            for (ItemListener l : ItemListener.all())
                l.onLoaded();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Executes a reactor.
     *
     * @param is
     *      If non-null, this can be consulted for ignoring some tasks. Only used during the initialization of Hudson.
     */
    private void executeReactor(final InitStrategy is, TaskBuilder... builders) throws IOException, InterruptedException, ReactorException {
        Reactor reactor = new Reactor(builders) {
            /**
             * Sets the thread name to the task for better diagnostics.
             */
            @Override
            protected void runTask(Task task) throws Exception {
                if (is!=null && is.skipInitTask(task))  return;

                SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);   // full access in the initialization thread
                String taskName = task.getDisplayName();

                Thread t = Thread.currentThread();
                String name = t.getName();
                if (taskName !=null)
                    t.setName(taskName);
                try {
                    long start = System.currentTimeMillis();
                    super.runTask(task);
                    if(LOG_STARTUP_PERFORMANCE)
                        LOGGER.info(String.format("Took %dms for %s by %s",
                                System.currentTimeMillis()-start, taskName, name));
                } finally {
                    t.setName(name);
                    SecurityContextHolder.clearContext();
                }
            }
        };

        ExecutorService es;
        if (PARALLEL_LOAD)
            es = new ThreadPoolExecutor(
                TWICE_CPU_NUM, TWICE_CPU_NUM, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());
        else
            es = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        try {
            reactor.execute(es,buildReactorListener());
        } finally {
            es.shutdownNow();   // upon a successful return the executor queue should be empty. Upon an exception, we want to cancel all pending tasks
        }
    }

    /**
     * Aggregates all the listeners into one and returns it.
     *
     * <p>
     * At this point plugins are not loaded yet, so we fall back to the META-INF/services look up to discover implementations.
     * As such there's no way for plugins to participate into this process.
     */
    private ReactorListener buildReactorListener() throws IOException {
        List<ReactorListener> r = (List) Service.loadInstances(Thread.currentThread().getContextClassLoader(), InitReactorListener.class);
        r.add(new ReactorListener() {
            final Level level = Level.parse( Configuration.getStringConfigParameter("initLogLevel", "FINE") );
            public void onTaskStarted(Task t) {
                LOGGER.log(level,"Started "+t.getDisplayName());
            }

            public void onTaskCompleted(Task t) {
                LOGGER.log(level,"Completed "+t.getDisplayName());
            }

            public void onTaskFailed(Task t, Throwable err, boolean fatal) {
                LOGGER.log(SEVERE, "Failed "+t.getDisplayName(),err);
            }

            public void onAttained(Milestone milestone) {
                Level lv = level;
                String s = "Attained "+milestone.toString();
                if (milestone instanceof InitMilestone) {
                    lv = Level.INFO; // noteworthy milestones --- at least while we debug problems further
                    initLevel = (InitMilestone)milestone;
                    s = initLevel.toString();
                }
                LOGGER.log(lv,s);
            }
        });
        return new ReactorListener.Aggregator(r);
    }

    public TcpSlaveAgentListener getTcpSlaveAgentListener() {
        return tcpSlaveAgentListener;
    }

    /**
     * Makes {@link AdjunctManager} URL-bound.
     * The dummy parameter allows us to use different URLs for the same adjunct,
     * for proper cache handling.
     */
    public AdjunctManager getAdjuncts(String dummy) {
        return adjuncts;
    }

    @Exported
    public int getSlaveAgentPort() {
        return slaveAgentPort;
    }

    public void setNodeName(String name) {
        throw new UnsupportedOperationException(); // not allowed
    }

    public String getNodeDescription() {
        return Messages.Hudson_NodeDescription();
    }

    @Exported
    public String getDescription() {
        return systemMessage;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public UpdateCenter getUpdateCenter() {
        return updateCenter;
    }

    public boolean isUsageStatisticsCollected() {
        return noUsageStatistics==null || !noUsageStatistics;
    }

    public void setNoUsageStatistics(Boolean noUsageStatistics) throws IOException {
        this.noUsageStatistics = noUsageStatistics;
        save();
    }

    public View.People getPeople() {
        return new View.People(this);
    }

    /**
     * Does this {@link View} has any associated user information recorded?
     */
    public boolean hasPeople() {
        return View.People.isApplicable(items.values());
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns a secret key that survives across container start/stop.
     * <p>
     * This value is useful for implementing some of the security features.
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Gets {@linkplain #getSecretKey() the secret key} as a key for AES-128.
     * @since 1.308
     */
    public SecretKey getSecretKeyAsAES128() {
        return Util.toAes128Key(secretKey);
    }

    /**
     * Gets the SCM descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<SCM> getScm(String shortClassName) {
        return findDescriptor(shortClassName,SCM.all());
    }

    /**
     * Gets the repository browser descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<RepositoryBrowser<?>> getRepositoryBrowser(String shortClassName) {
        return findDescriptor(shortClassName,RepositoryBrowser.all());
    }

    /**
     * Gets the builder descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<Builder> getBuilder(String shortClassName) {
        return findDescriptor(shortClassName, Builder.all());
    }

    /**
     * Gets the build wrapper descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<BuildWrapper> getBuildWrapper(String shortClassName) {
        return findDescriptor(shortClassName, BuildWrapper.all());
    }

    /**
     * Gets the publisher descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<Publisher> getPublisher(String shortClassName) {
        return findDescriptor(shortClassName, Publisher.all());
    }

    /**
     * Gets the trigger descriptor by name. Primarily used for making them web-visible.
     */
    public TriggerDescriptor getTrigger(String shortClassName) {
        return (TriggerDescriptor) findDescriptor(shortClassName, Trigger.all());
    }

    /**
     * Gets the retention strategy descriptor by name. Primarily used for making them web-visible.
     */
    public Descriptor<RetentionStrategy<?>> getRetentionStrategy(String shortClassName) {
        return findDescriptor(shortClassName, RetentionStrategy.all());
    }

    /**
     * Gets the {@link JobPropertyDescriptor} by name. Primarily used for making them web-visible.
     */
    public JobPropertyDescriptor getJobProperty(String shortClassName) {
        // combining these two lines triggers javac bug. See issue #610.
        Descriptor d = findDescriptor(shortClassName, JobPropertyDescriptor.all());
        return (JobPropertyDescriptor) d;
    }

    /**
     * @deprecated
     *      UI method. Not meant to be used programatically.
     */
    public ComputerSet getComputer() {
        return new ComputerSet();
    }

    /**
     * Exposes {@link Descriptor} by its name to URL.
     *
     * After doing all the {@code getXXX(shortClassName)} methods, I finally realized that
     * this just doesn't scale.
     *
     * @param id
     *      Either {@link Descriptor#getId()} (recommended) or the short name of a {@link Describable} subtype (for compatibility)
     */
    public Descriptor getDescriptor(String id) {
        // legacy descriptors that are reigstered manually doesn't show up in getExtensionList, so check them explicitly.
        for( Descriptor d : Iterators.sequence(getExtensionList(Descriptor.class),DescriptorExtensionList.listLegacyInstances()) ) {
            String name = d.getId();
            if(name.equals(id))
                return d;
            if(name.substring(name.lastIndexOf('.')+1).equals(id))
                return d;
        }
        return null;
    }

    /**
     * Alias for {@link #getDescriptor(String)}.
     */
    public Descriptor getDescriptorByName(String id) {
        return getDescriptor(id);
    }

    /**
     * Gets the {@link Descriptor} that corresponds to the given {@link Describable} type.
     * <p>
     * If you have an instance of {@code type} and call {@link Describable#getDescriptor()},
     * you'll get the same instance that this method returns.
     */
    public Descriptor getDescriptor(Class<? extends Describable> type) {
        for( Descriptor d : getExtensionList(Descriptor.class) )
            if(d.clazz==type)
                return d;
        return null;
    }

    /**
     * Works just like {@link #getDescriptor(Class)} but don't take no for an answer.
     *
     * @throws AssertionError
     *      If the descriptor is missing.
     * @since 1.326
     */
    public Descriptor getDescriptorOrDie(Class<? extends Describable> type) {
        Descriptor d = getDescriptor(type);
        if (d==null)
            throw new AssertionError(type+" is missing its descriptor");
        return d;
    }

    /**
     * Gets the {@link Descriptor} instance in the current Hudson by its type.
     */
    public <T extends Descriptor> T getDescriptorByType(Class<T> type) {
        for( Descriptor d : getExtensionList(Descriptor.class) )
            if(d.getClass()==type)
                return type.cast(d);
        return null;
    }

    /**
     * Gets the {@link SecurityRealm} descriptors by name. Primarily used for making them web-visible.
     */
    public Descriptor<SecurityRealm> getSecurityRealms(String shortClassName) {
        return findDescriptor(shortClassName,SecurityRealm.all());
    }

    /**
     * Finds a descriptor that has the specified name.
     */
    private <T extends Describable<T>>
    Descriptor<T> findDescriptor(String shortClassName, Collection<? extends Descriptor<T>> descriptors) {
        String name = '.'+shortClassName;
        for (Descriptor<T> d : descriptors) {
            if(d.clazz.getName().endsWith(name))
                return d;
        }
        return null;
    }

    protected void updateComputerList() throws IOException {
        updateComputerList(AUTOMATIC_SLAVE_LAUNCH);
    }

    /**
     * Gets all the installed {@link SCMListener}s.
     */
    public CopyOnWriteList<SCMListener> getSCMListeners() {
        return scmListeners;
    }

    /**
     * Gets the plugin object from its short name.
     *
     * <p>
     * This allows URL <tt>hudson/plugin/ID</tt> to be served by the views
     * of the plugin class.
     */
    public Plugin getPlugin(String shortName) {
        PluginWrapper p = pluginManager.getPlugin(shortName);
        if(p==null)     return null;
        return p.getPlugin();
    }

    /**
     * Gets the plugin object from its class.
     *
     * <p>
     * This allows easy storage of plugin information in the plugin singleton without
     * every plugin reimplementing the singleton pattern.
     *
     * @param clazz The plugin class (beware class-loader fun, this will probably only work
     * from within the hpi that defines the plugin class, it may or may not work in other cases)
     *
     * @return The plugin instance.
     */
    @SuppressWarnings("unchecked")
    public <P extends Plugin> P getPlugin(Class<P> clazz) {
        PluginWrapper p = pluginManager.getPlugin(clazz);
        if(p==null)     return null;
        return (P) p.getPlugin();
    }

    /**
     * Gets the plugin objects from their super-class.
     *
     * @param clazz The plugin class (beware class-loader fun)
     *
     * @return The plugin instances.
     */
    public <P extends Plugin> List<P> getPlugins(Class<P> clazz) {
        List<P> result = new ArrayList<P>();
        for (PluginWrapper w: pluginManager.getPlugins(clazz)) {
            result.add((P)w.getPlugin());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Synonym to {@link #getNodeDescription()}.
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * Gets the markup formatter used in the system.
     *
     * @return
     *      never null.
     * @since 1.391
     */
    public MarkupFormatter getMarkupFormatter() {
        return markupFormatter!=null ? markupFormatter : RawHtmlMarkupFormatter.INSTANCE;
    }

    /**
     * Sets the markup formatter used in the system globally.
     *
     * @since 1.391
     */
    public void setMarkupFormatter(MarkupFormatter f) {
        this.markupFormatter = f;
    }

    /**
     * Sets the system message.
     */
    public void setSystemMessage(String message) throws IOException {
        this.systemMessage = message;
        save();
    }

    public FederatedLoginService getFederatedLoginService(String name) {
        for (FederatedLoginService fls : FederatedLoginService.all()) {
            if (fls.getUrlName().equals(name))
                return fls;
        }
        return null;
    }

    public List<FederatedLoginService> getFederatedLoginServices() {
        return FederatedLoginService.all();
    }

    public Launcher createLauncher(TaskListener listener) {
        return new LocalLauncher(listener).decorateFor(this);
    }


    public String getFullName() {
        return "";
    }

    public String getFullDisplayName() {
        return "";
    }

    /**
     * Returns the transient {@link Action}s associated with the top page.
     *
     * <p>
     * Adding {@link Action} is primarily useful for plugins to contribute
     * an item to the navigation bar of the top page. See existing {@link Action}
     * implementation for it affects the GUI.
     *
     * <p>
     * To register an {@link Action}, implement {@link RootAction} extension point, or write code like
     * {@code Hudson.getInstance().getActions().add(...)}.
     *
     * @return
     *      Live list where the changes can be made. Can be empty but never null.
     * @since 1.172
     */
    public List<Action> getActions() {
        return actions;
    }

    /**
     * Gets just the immediate children of {@link Jenkins}.
     *
     * @see #getAllItems(Class)
     */
    @Exported(name="jobs")
    public List<TopLevelItem> getItems() {
        List<TopLevelItem> viewableItems = new ArrayList<TopLevelItem>();
        for (TopLevelItem item : items.values()) {
            if (item.hasPermission(Item.READ))
                viewableItems.add(item);
        }

        return viewableItems;
    }

    /**
     * Returns the read-only view of all the {@link TopLevelItem}s keyed by their names.
     * <p>
     * This method is efficient, as it doesn't involve any copying.
     *
     * @since 1.296
     */
    public Map<String,TopLevelItem> getItemMap() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * Gets just the immediate children of {@link Jenkins} but of the given type.
     */
    public <T> List<T> getItems(Class<T> type) {
        List<T> r = new ArrayList<T>();
        for (TopLevelItem i : getItems())
            if (type.isInstance(i))
                 r.add(type.cast(i));
        return r;
    }

    /**
     * Gets all the {@link Item}s recursively in the {@link ItemGroup} tree
     * and filter them by the given type.
     */
    public <T extends Item> List<T> getAllItems(Class<T> type) {
        List<T> r = new ArrayList<T>();

        Stack<ItemGroup> q = new Stack<ItemGroup>();
        q.push(this);

        while(!q.isEmpty()) {
            ItemGroup<?> parent = q.pop();
            for (Item i : parent.getItems()) {
                if(type.isInstance(i)) {
                    if (i.hasPermission(Item.READ))
                        r.add(type.cast(i));
                }
                if(i instanceof ItemGroup)
                    q.push((ItemGroup)i);
            }
        }

        return r;
    }

    /**
     * Gets all the items recursively.
     *
     * @since 1.402
     */
    public List<Item> getAllItems() {
        return getAllItems(Item.class);
    }

    /**
     * Gets the list of all the projects.
     *
     * <p>
     * Since {@link Project} can only show up under {@link Jenkins},
     * no need to search recursively.
     */
    public List<Project> getProjects() {
        return Util.createSubList(items.values(),Project.class);
    }

    /**
     * Gets the names of all the {@link Job}s.
     */
    public Collection<String> getJobNames() {
        List<String> names = new ArrayList<String>();
        for (Job j : getAllItems(Job.class))
            names.add(j.getFullName());
        return names;
    }

    public List<Action> getViewActions() {
        return getActions();
    }

    /**
     * Gets the names of all the {@link TopLevelItem}s.
     */
    public Collection<String> getTopLevelItemNames() {
        List<String> names = new ArrayList<String>();
        for (TopLevelItem j : items.values())
            names.add(j.getName());
        return names;
    }

    public synchronized View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    /**
     * Gets the read-only list of all {@link View}s.
     */
    @Exported
    public synchronized Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    public void addView(View v) throws IOException {
        viewGroupMixIn.addView(v);
    }

    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    public synchronized void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view,oldName,newName);
    }

    /**
     * Returns the primary {@link View} that renders the top-page of Hudson.
     */
    @Exported
    public View getPrimaryView() {
        return viewGroupMixIn.getPrimaryView();
     }

    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    public Jenkins getItemGroup() {
        return this;
   }

    public MyViewsTabBar getMyViewsTabBar() {
        return myViewsTabBar;
    }

    /**
     * Returns true if the current running Hudson is upgraded from a version earlier than the specified version.
     *
     * <p>
     * This method continues to return true until the system configuration is saved, at which point
     * {@link #version} will be overwritten and Hudson forgets the upgrade history.
     *
     * <p>
     * To handle SNAPSHOTS correctly, pass in "1.N.*" to test if it's upgrading from the version
     * equal or younger than N. So say if you implement a feature in 1.301 and you want to check
     * if the installation upgraded from pre-1.301, pass in "1.300.*"
     *
     * @since 1.301
     */
    public boolean isUpgradedFromBefore(VersionNumber v) {
        try {
            return new VersionNumber(version).isOlderThan(v);
        } catch (IllegalArgumentException e) {
            // fail to parse this version number
            return false;
        }
    }

    /**
     * Gets the read-only list of all {@link Computer}s.
     */
    public Computer[] getComputers() {
        Computer[] r = computers.values().toArray(new Computer[computers.size()]);
        Arrays.sort(r,new Comparator<Computer>() {
            final Collator collator = Collator.getInstance();
            public int compare(Computer lhs, Computer rhs) {
                if(lhs.getNode()==Jenkins.this)  return -1;
                if(rhs.getNode()==Jenkins.this)  return 1;
                return collator.compare(lhs.getDisplayName(), rhs.getDisplayName());
            }
        });
        return r;
    }

    @CLIResolver
    public Computer getComputer(@Argument(required=true,metaVar="NAME",usage="Node name") String name) {
        if(name.equals("(master)"))
            name = "";

        for (Computer c : computers.values()) {
            if(c.getName().equals(name))
                return c;
        }
        return null;
    }

    /**
     * Gets the label that exists on this system by the name.
     *
     * @return null if name is null.
     * @see Label#parseExpression(String) (String)
     */
    public Label getLabel(String expr) {
        if(expr==null)  return null;
        while(true) {
            Label l = labels.get(expr);
            if(l!=null)
                return l;

            // non-existent
            try {
                labels.putIfAbsent(expr,Label.parseExpression(expr));
            } catch (ANTLRException e) {
                // laxly accept it as a single label atom for backward compatibility
                return getLabelAtom(expr);
            }
        }
    }

    /**
     * Returns the label atom of the given name.
     */
    public LabelAtom getLabelAtom(String name) {
        if (name==null)  return null;

        while(true) {
            Label l = labels.get(name);
            if(l!=null)
                return (LabelAtom)l;

            // non-existent
            LabelAtom la = new LabelAtom(name);
            if (labels.putIfAbsent(name, la)==null)
                la.load();
        }
    }

    /**
     * Gets all the active labels in the current system.
     */
    public Set<Label> getLabels() {
        Set<Label> r = new TreeSet<Label>();
        for (Label l : labels.values()) {
            if(!l.isEmpty() && ! l.isSelfLabel())
                r.add(l);
        }
        return r;
    }

    public Set<LabelAtom> getLabelAtoms() {
        Set<LabelAtom> r = new TreeSet<LabelAtom>();
        for (Label l : labels.values()) {
            if(!l.isEmpty() && l instanceof LabelAtom)
                r.add((LabelAtom)l);
        }
        return r;
    }

    public Queue getQueue() {
        return queue;
    }

    @Override
    public String getDisplayName() {
        return Messages.Hudson_DisplayName();
    }

    public List<JDK> getJDKs() {
        if(jdks==null)
            jdks = new ArrayList<JDK>();
        return jdks;
    }

    /**
     * Gets the JDK installation of the given name, or returns null.
     */
    public JDK getJDK(String name) {
        if(name==null) {
            // if only one JDK is configured, "default JDK" should mean that JDK.
            List<JDK> jdks = getJDKs();
            if(jdks.size()==1)  return jdks.get(0);
            return null;
        }
        for (JDK j : getJDKs()) {
            if(j.getName().equals(name))
                return j;
        }
        return null;
    }



    /**
     * Gets the slave node of the give name, hooked under this Hudson.
     */
    public Node getNode(String name) {
        for (Node s : getNodes()) {
            if(s.getNodeName().equals(name))
                return s;
        }
        return null;
    }

    /**
     * Gets a {@link Cloud} by {@link Cloud#name its name}, or null.
     */
    public Cloud getCloud(String name) {
        return clouds.getByName(name);
    }

    protected Map<Node,Computer> getComputerMap() {
        return computers;
    }

    /**
     * Returns all {@link Node}s in the system, excluding {@link Jenkins} instance itself which
     * represents the master.
     */
    public List<Node> getNodes() {
        return Collections.unmodifiableList(slaves);
    }

    /**
     * Adds one more {@link Node} to Hudson.
     */
    public synchronized void addNode(Node n) throws IOException {
        if(n==null)     throw new IllegalArgumentException();
        ArrayList<Node> nl = new ArrayList<Node>(this.slaves);
        if(!nl.contains(n)) // defensive check
            nl.add(n);
        setNodes(nl);
    }

    /**
     * Removes a {@link Node} from Hudson.
     */
    public synchronized void removeNode(Node n) throws IOException {
        Computer c = n.toComputer();
        if (c!=null)
            c.disconnect(OfflineCause.create(Messages._Hudson_NodeBeingRemoved()));

        ArrayList<Node> nl = new ArrayList<Node>(this.slaves);
        nl.remove(n);
        setNodes(nl);
    }

    public void setNodes(List<? extends Node> nodes) throws IOException {
        // make sure that all names are unique
        Set<String> names = new HashSet<String>();
        for (Node n : nodes)
            if(!names.add(n.getNodeName()))
                throw new IllegalArgumentException(n.getNodeName()+" is defined more than once");
        this.slaves = new NodeList(nodes);
        updateComputerList();
        trimLabels();
        save();
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
    	return nodeProperties;
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getGlobalNodeProperties() {
    	return globalNodeProperties;
    }

    /**
     * Resets all labels and remove invalid ones.
     */
    private void trimLabels() {
        for (Iterator<Label> itr = labels.values().iterator(); itr.hasNext();) {
            Label l = itr.next();
            resetLabel(l);
            if(l.isEmpty())
                itr.remove();
        }
    }

    /**
     * Binds {@link AdministrativeMonitor}s to URL.
     */
    public AdministrativeMonitor getAdministrativeMonitor(String id) {
        for (AdministrativeMonitor m : administrativeMonitors)
            if(m.id.equals(id))
                return m;
        return null;
    }

    public NodeDescriptor getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    public static final class DescriptorImpl extends NodeDescriptor {
        @Extension
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        public String getDisplayName() {
            return "";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }

        // to route /descriptor/FQCN/xxx to getDescriptor(FQCN).xxx
        public Object getDynamic(String token) {
            return Jenkins.getInstance().getDescriptor(token);
        }
    }

    /**
     * Gets the system default quiet period.
     */
    public int getQuietPeriod() {
        return quietPeriod!=null ? quietPeriod : 5;
    }

    /**
     * Gets the global SCM check out retry count.
     */
    public int getScmCheckoutRetryCount() {
        return scmCheckoutRetryCount;
    }

    @Override
    public String getSearchUrl() {
        return "";
    }

    @Override
    public SearchIndexBuilder makeSearchIndex() {
        return super.makeSearchIndex()
            .add("configure", "config","configure")
            .add("manage")
            .add("log")
            .add(getPrimaryView().makeSearchIndex())
            .add(new CollectionSearchIndex() {// for computers
                protected Computer get(String key) { return getComputer(key); }
                protected Collection<Computer> all() { return computers.values(); }
            })
            .add(new CollectionSearchIndex() {// for users
                protected User get(String key) { return User.get(key,false); }
                protected Collection<User> all() { return User.getAll(); }
            })
            .add(new CollectionSearchIndex() {// for views
                protected View get(String key) { return getView(key); }
                protected Collection<View> all() { return views; }
            });
    }

    public String getUrlChildPrefix() {
        return "job";
    }

    /**
     * Gets the absolute URL of Jenkins,
     * such as "http://localhost/jenkins/".
     *
     * <p>
     * This method first tries to use the manually configured value, then
     * fall back to {@link StaplerRequest#getRootPath()}.
     * It is done in this order so that it can work correctly even in the face
     * of a reverse proxy.
     *
     * @return
     *      This method returns null if this parameter is not configured by the user.
     *      The caller must gracefully deal with this situation.
     *      The returned URL will always have the trailing '/'.
     * @since 1.66
     * @see Descriptor#getCheckUrl(String)
     * @see #getRootUrlFromRequest()
     */
    public String getRootUrl() {
        // for compatibility. the actual data is stored in Mailer
        String url = Mailer.descriptor().getUrl();
        if(url!=null)   return url;

        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null)
            return getRootUrlFromRequest();
        return null;
    }

    /**
     * Is Jenkins running in HTTPS?
     *
     * Note that we can't really trust {@link StaplerRequest#isSecure()} because HTTPS might be terminated
     * in the reverse proxy.
     */
    public boolean isRootUrlSecure() {
        String url = getRootUrl();
        return url!=null && url.startsWith("https");
    }

    /**
     * Gets the absolute URL of Hudson top page, such as "http://localhost/hudson/".
     *
     * <p>
     * Unlike {@link #getRootUrl()}, which uses the manually configured value,
     * this one uses the current request to reconstruct the URL. The benefit is
     * that this is immune to the configuration mistake (users often fail to set the root URL
     * correctly, especially when a migration is involved), but the downside
     * is that unless you are processing a request, this method doesn't work.
     *
     * @since 1.263
     */
    public String getRootUrlFromRequest() {
        StaplerRequest req = Stapler.getCurrentRequest();
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()+"://");
        buf.append(req.getServerName());
        if(req.getServerPort()!=80)
            buf.append(':').append(req.getServerPort());
        buf.append(req.getContextPath()).append('/');
        return buf.toString();
    }

    public File getRootDir() {
        return root;
    }

    public FilePath getWorkspaceFor(TopLevelItem item) {
        return new FilePath(expandVariablesForDirectory(workspaceDir, item));
    }

    public File getBuildDirFor(Job job) {
        return expandVariablesForDirectory(buildsDir, job);
    }

    private File expandVariablesForDirectory(String base, Item item) {
        return new File(Util.replaceMacro(base, ImmutableMap.of(
                "JENKINS_HOME", getRootDir().getPath(),
                "ITEM_ROOTDIR", item.getRootDir().getPath(),
                "ITEM_FULLNAME", item.getFullName())));
    }
    
    public String getRawWorkspaceDir() {
        return workspaceDir;
    }

    public String getRawBuildsDir() {
        return buildsDir;
    }

    public FilePath getRootPath() {
        return new FilePath(getRootDir());
    }

    @Override
    public FilePath createPath(String absolutePath) {
        return new FilePath((VirtualChannel)null,absolutePath);
    }

    public ClockDifference getClockDifference() {
        return ClockDifference.ZERO;
    }

    /**
     * For binding {@link LogRecorderManager} to "/log".
     * Everything below here is admin-only, so do the check here.
     */
    public LogRecorderManager getLog() {
        checkPermission(ADMINISTER);
        return log;
    }

    /**
     * A convenience method to check if there's some security
     * restrictions in place.
     */
    @Exported
    public boolean isUseSecurity() {
        return securityRealm!=SecurityRealm.NO_AUTHENTICATION || authorizationStrategy!=AuthorizationStrategy.UNSECURED;
    }

    /**
     * If true, all the POST requests to Hudson would have to have crumb in it to protect
     * Hudson from CSRF vulnerabilities.
     */
    @Exported
    public boolean isUseCrumbs() {
        return crumbIssuer!=null;
    }

    /**
     * Returns the constant that captures the three basic security modes
     * in Hudson.
     */
    public SecurityMode getSecurity() {
        // fix the variable so that this code works under concurrent modification to securityRealm.
        SecurityRealm realm = securityRealm;

        if(realm==SecurityRealm.NO_AUTHENTICATION)
            return SecurityMode.UNSECURED;
        if(realm instanceof LegacySecurityRealm)
            return SecurityMode.LEGACY;
        return SecurityMode.SECURED;
    }

    /**
     * @return
     *      never null.
     */
    public SecurityRealm getSecurityRealm() {
        return securityRealm;
    }

    public void setSecurityRealm(SecurityRealm securityRealm) {
        if(securityRealm==null)
            securityRealm= SecurityRealm.NO_AUTHENTICATION;
        this.securityRealm = securityRealm;
        // reset the filters and proxies for the new SecurityRealm
        try {
            HudsonFilter filter = HudsonFilter.get(servletContext);
            if (filter == null) {
                // Fix for #3069: This filter is not necessarily initialized before the servlets.
                // when HudsonFilter does come back, it'll initialize itself.
                LOGGER.fine("HudsonFilter has not yet been initialized: Can't perform security setup for now");
            } else {
                LOGGER.fine("HudsonFilter has been previously initialized: Setting security up");
                filter.reset(securityRealm);
                LOGGER.fine("Security is now fully set up");
            }
        } catch (ServletException e) {
            // for binary compatibility, this method cannot throw a checked exception
            throw new AcegiSecurityException("Failed to configure filter",e) {};
        }
    }

    public void setAuthorizationStrategy(AuthorizationStrategy a) {
        if (a == null)
            a = AuthorizationStrategy.UNSECURED;
        authorizationStrategy = a;
    }

    public Lifecycle getLifecycle() {
        return Lifecycle.get();
    }

    /**
     * Returns {@link ExtensionList} that retains the discovered instances for the given extension type.
     *
     * @param extensionType
     *      The base type that represents the extension point. Normally {@link ExtensionPoint} subtype
     *      but that's not a hard requirement.
     * @return
     *      Can be an empty list but never null.
     */
    @SuppressWarnings({"unchecked"})
    public <T> ExtensionList<T> getExtensionList(Class<T> extensionType) {
        return extensionLists.get(extensionType);
    }

    /**
     * Used to bind {@link ExtensionList}s to URLs.
     *
     * @since 1.349
     */
    public ExtensionList getExtensionList(String extensionType) throws ClassNotFoundException {
        return getExtensionList(pluginManager.uberClassLoader.loadClass(extensionType));
    }

    /**
     * Returns {@link ExtensionList} that retains the discovered {@link Descriptor} instances for the given
     * kind of {@link Describable}.
     *
     * @return
     *      Can be an empty list but never null.
     */
    @SuppressWarnings({"unchecked"})
    public <T extends Describable<T>,D extends Descriptor<T>> DescriptorExtensionList<T,D> getDescriptorList(Class<T> type) {
        return descriptorLists.get(type);
    }

    /**
     * Returns the root {@link ACL}.
     *
     * @see AuthorizationStrategy#getRootACL()
     */
    @Override
    public ACL getACL() {
        return authorizationStrategy.getRootACL();
    }

    /**
     * @return
     *      never null.
     */
    public AuthorizationStrategy getAuthorizationStrategy() {
        return authorizationStrategy;
    }

    /**
     * Returns true if Hudson is quieting down.
     * <p>
     * No further jobs will be executed unless it
     * can be finished while other current pending builds
     * are still in progress.
     */
    public boolean isQuietingDown() {
        return isQuietingDown;
    }

    /**
     * Returns true if the container initiated the termination of the web application.
     */
    public boolean isTerminating() {
        return terminating;
    }

    /**
     * Gets the initialization milestone that we've already reached.
     *
     * @return
     *      {@link InitMilestone#STARTED} even if the initialization hasn't been started, so that this method
     *      never returns null.
     */
    public InitMilestone getInitLevel() {
        return initLevel;
    }

    public void setNumExecutors(int n) throws IOException {
        this.numExecutors = n;
        save();
    }



    /**
     * {@inheritDoc}.
     *
     * Note that the look up is case-insensitive.
     */
    public TopLevelItem getItem(String name) {
        if (name==null)    return null;
    	TopLevelItem item = items.get(name);
        if (item==null || !item.hasPermission(Item.READ))
            return null;
        return item;
    }

    /**
     * Gets the item by its relative name from the given context
     *
     * <h2>Relative Names</h2>
     * <p>
     * If the name starts from '/', like "/foo/bar/zot", then it's interpreted as absolute.
     * Otherwise, the name should be something like "../foo/bar" and it's interpreted like
     * relative path name is, against the given context.
     *
     * @param context
     *      null is interpreted as {@link Jenkins}. Base 'directory' of the interpretation.
     * @since 1.406
     */
    public Item getItem(String relativeName, ItemGroup context) {
        if (context==null)  context = this;

        if (relativeName.startsWith("/"))   // absolute
            return getItemByFullName(relativeName);

        Object/*Item|ItemGroup*/ ctx = context;

        StringTokenizer tokens = new StringTokenizer(relativeName,"/");
        while (tokens.hasMoreTokens()) {
            String s = tokens.nextToken();
            if (s.equals("..")) {
                if (ctx instanceof Item) {
                    ctx = ((Item)ctx).getParent();
                    continue;
                }

                ctx=null;    // can't go up further
                break;
            }
            if (s.equals(".")) {
                continue;
            }

            if (ctx instanceof ItemGroup) {
                ItemGroup g = (ItemGroup) ctx;
                Item i = g.getItem(s);
                if (i==null || !i.hasPermission(Item.READ)) {
                    ctx=null;    // can't go up further
                    break;
                }
                ctx=i;
            }
        }

        if (ctx instanceof Item)
            return (Item)ctx;

        // fall back to the classic interpretation
        return getItemByFullName(relativeName);
    }

    public final Item getItem(String relativeName, Item context) {
        return getItem(relativeName,context!=null?context.getParent():null);
    }

    public final <T extends Item> T getItem(String relativeName, ItemGroup context, Class<T> type) {
        Item r = getItem(relativeName, context);
        if (type.isInstance(r))
            return type.cast(r);
        return null;
    }

    public final <T extends Item> T getItem(String relativeName, Item context, Class<T> type) {
        return getItem(relativeName,context!=null?context.getParent():null,type);
    }

    public File getRootDirFor(TopLevelItem child) {
        return getRootDirFor(child.getName());
    }

    private File getRootDirFor(String name) {
        return new File(new File(getRootDir(),"jobs"), name);
    }

    /**
     * Gets the {@link Item} object by its full name.
     * Full names are like path names, where each name of {@link Item} is
     * combined by '/'.
     *
     * @return
     *      null if either such {@link Item} doesn't exist under the given full name,
     *      or it exists but it's no an instance of the given type.
     */
    public <T extends Item> T getItemByFullName(String fullName, Class<T> type) {
        StringTokenizer tokens = new StringTokenizer(fullName,"/");
        ItemGroup parent = this;

        if(!tokens.hasMoreTokens()) return null;    // for example, empty full name.

        while(true) {
            Item item = parent.getItem(tokens.nextToken());
            if(!tokens.hasMoreTokens()) {
                if(type.isInstance(item))
                    return type.cast(item);
                else
                    return null;
            }

            if(!(item instanceof ItemGroup))
                return null;    // this item can't have any children

            if (!item.hasPermission(Item.READ))
                return null;

            parent = (ItemGroup) item;
        }
    }

    public Item getItemByFullName(String fullName) {
        return getItemByFullName(fullName,Item.class);
    }

    /**
     * Gets the user of the given name.
     *
     * @return
     *      This method returns a non-null object for any user name, without validation.
     */
    public User getUser(String name) {
        return User.get(name);
    }

    /**
     * Creates a new job.
     *
     * @throws IllegalArgumentException
     *      if the project of the given name already exists.
     */
    public synchronized TopLevelItem createProject( TopLevelItemDescriptor type, String name ) throws IOException {
        return createProject(type, name, true);
    }

    /**
     * Creates a new job.
     * @param type Descriptor for job type
     * @param name Name for job
     * @param notify Whether to fire onCreated method for all ItemListeners
     * @throws IllegalArgumentException
     *      if a project of the give name already exists.
     */
    public synchronized TopLevelItem createProject( TopLevelItemDescriptor type, String name, boolean notify ) throws IOException {
        return itemGroupMixIn.createProject(type,name,notify);
    }

    /**
     * Overwrites the existing item by new one.
     *
     * <p>
     * This is a short cut for deleting an existing job and adding a new one.
     */
    public synchronized void putItem(TopLevelItem item) throws IOException, InterruptedException {
        String name = item.getName();
        TopLevelItem old = items.get(name);
        if (old ==item)  return; // noop

        checkPermission(Item.CREATE);
        if (old!=null)
            old.delete();
        items.put(name,item);
        ItemListener.fireOnCreated(item);
    }

    /**
     * Creates a new job.
     *
     * <p>
     * This version infers the descriptor from the type of the top-level item.
     *
     * @throws IllegalArgumentException
     *      if the project of the given name already exists.
     */
    public synchronized <T extends TopLevelItem> T createProject( Class<T> type, String name ) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptor)getDescriptor(type),name));
    }

    /**
     * Called by {@link Job#renameTo(String)} to update relevant data structure.
     * assumed to be synchronized on Hudson by the caller.
     */
    public void onRenamed(TopLevelItem job, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName,job);

        for (View v : views)
            v.onJobRenamed(job, oldName, newName);
        save();
    }

    /**
     * Called in response to {@link Job#doDoDelete(StaplerRequest, StaplerResponse)}
     */
    public void onDeleted(TopLevelItem item) throws IOException {
        for (ItemListener l : ItemListener.all())
            l.onDeleted(item);

        items.remove(item.getName());
        for (View v : views)
            v.onJobRenamed(item, item.getName(), null);
        save();
    }

    public FingerprintMap getFingerprintMap() {
        return fingerprintMap;
    }

    // if no finger print matches, display "not found page".
    public Object getFingerprint( String md5sum ) throws IOException {
        Fingerprint r = fingerprintMap.get(md5sum);
        if(r==null)     return new NoFingerprintMatch(md5sum);
        else            return r;
    }

    /**
     * Gets a {@link Fingerprint} object if it exists.
     * Otherwise null.
     */
    public Fingerprint _getFingerprint( String md5sum ) throws IOException {
        return fingerprintMap.get(md5sum);
    }

    /**
     * The file we save our configuration.
     */
    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(root,"config.xml"));
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public Mode getMode() {
        return mode;
    }

    public String getLabelString() {
        return fixNull(label).trim();
    }

    @Override
    public LabelAtom getSelfLabel() {
        return getLabelAtom("master");
    }

    public Computer createComputer() {
        return new Hudson.MasterComputer();
    }

    private synchronized TaskBuilder loadTasks() throws IOException {
        File projectsDir = new File(root,"jobs");
        if(!projectsDir.isDirectory() && !projectsDir.mkdirs()) {
            if(projectsDir.exists())
                throw new IOException(projectsDir+" is not a directory");
            throw new IOException("Unable to create "+projectsDir+"\nPermission issue? Please create this directory manually.");
        }
        File[] subdirs = projectsDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory() && Items.getConfigFile(child).exists();
            }
        });

        TaskGraphBuilder g = new TaskGraphBuilder();
        Handle loadHudson = g.requires(EXTENSIONS_AUGMENTED).attains(JOB_LOADED).add("Loading global config", new Executable() {
            public void run(Reactor session) throws Exception {
                XmlFile cfg = getConfigFile();
                if (cfg.exists()) {
                    // reset some data that may not exist in the disk file
                    // so that we can take a proper compensation action later.
                    primaryView = null;
                    views.clear();

                    // load from disk
                    cfg.unmarshal(Jenkins.this);
                }

                // if we are loading old data that doesn't have this field
                if (slaves == null) slaves = new NodeList();

                clouds.setOwner(Jenkins.this);
                items.clear();
            }
        });

        for (final File subdir : subdirs) {
            g.requires(loadHudson).attains(JOB_LOADED).notFatal().add("Loading job "+subdir.getName(),new Executable() {
                public void run(Reactor session) throws Exception {
                    TopLevelItem item = (TopLevelItem) Items.load(Jenkins.this, subdir);
                    items.put(item.getName(), item);
                }
            });
        }

        g.requires(JOB_LOADED).add("Finalizing set up",new Executable() {
            public void run(Reactor session) throws Exception {
                rebuildDependencyGraph();

                {// recompute label objects - populates the labels mapping.
                    for (Node slave : slaves)
                        // Note that not all labels are visible until the slaves have connected.
                        slave.getAssignedLabels();
                    getAssignedLabels();
                }

                // initialize views by inserting the default view if necessary
                // this is both for clean Hudson and for backward compatibility.
                if(views.size()==0 || primaryView==null) {
                    View v = new AllView(Messages.Hudson_ViewName());
                    setViewOwner(v);
                    views.add(0,v);
                    primaryView = v.getViewName();
                }

                // read in old data that doesn't have the security field set
                if(authorizationStrategy==null) {
                    if(useSecurity==null || !useSecurity)
                        authorizationStrategy = AuthorizationStrategy.UNSECURED;
                    else
                        authorizationStrategy = new LegacyAuthorizationStrategy();
                }
                if(securityRealm==null) {
                    if(useSecurity==null || !useSecurity)
                        setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
                    else
                        setSecurityRealm(new LegacySecurityRealm());
                } else {
                    // force the set to proxy
                    setSecurityRealm(securityRealm);
                }

                if(useSecurity!=null && !useSecurity) {
                    // forced reset to the unsecure mode.
                    // this works as an escape hatch for people who locked themselves out.
                    authorizationStrategy = AuthorizationStrategy.UNSECURED;
                    setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
                }

                // Initialize the filter with the crumb issuer
                setCrumbIssuer(crumbIssuer);

                // auto register root actions
                for (Action a : getExtensionList(RootAction.class))
                    if (!actions.contains(a)) actions.add(a);
            }
        });

        return g;
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }


    /**
     * Called to shut down the system.
     */
    public void cleanUp() {
        Set<Future<?>> pending = new HashSet<Future<?>>();
        terminating = true;
        for( Computer c : computers.values() ) {
            c.interrupt();
            killComputer(c);
            pending.add(c.disconnect(null));
        }
        if(udpBroadcastThread!=null)
            udpBroadcastThread.shutdown();
        if(dnsMultiCast!=null)
            dnsMultiCast.close();
        interruptReloadThread();
        Trigger.timer.cancel();
        // TODO: how to wait for the completion of the last job?
        Trigger.timer = null;
        if(tcpSlaveAgentListener!=null)
            tcpSlaveAgentListener.shutdown();

        if(pluginManager!=null) // be defensive. there could be some ugly timing related issues
            pluginManager.stop();

        if(getRootDir().exists())
            // if we are aborting because we failed to create JENKINS_HOME,
            // don't try to save. Issue #536
            getQueue().save();

        threadPoolForLoad.shutdown();
        for (Future<?> f : pending)
            try {
                f.get(10, TimeUnit.SECONDS);    // if clean up operation didn't complete in time, we fail the test
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;  // someone wants us to die now. quick!
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Failed to shut down properly",e);
            } catch (TimeoutException e) {
                LOGGER.log(Level.WARNING, "Failed to shut down properly",e);
            }

        LogFactory.releaseAll();

        theInstance = null;
    }

    public Object getDynamic(String token) {
        for (Action a : getActions()) {
            String url = a.getUrlName();
            if (url==null)  continue;
            if (url.equals(token) || url.equals('/' + token))
                return a;
        }
        for (Action a : getManagementLinks())
            if(a.getUrlName().equals(token))
                return a;
        return null;
    }


//
//
// actions
//
//
    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        BulkChange bc = new BulkChange(this);
        try {
            checkPermission(ADMINISTER);

            JSONObject json = req.getSubmittedForm();

	    if (json.has("markupFormatter")) {
		markupFormatter = req.bindJSON(MarkupFormatter.class,json.getJSONObject("markupFormatter"));
	    } else {
		markupFormatter = null;
	    }

            if (json.has("viewsTabBar")) {
                viewsTabBar = req.bindJSON(ViewsTabBar.class,json.getJSONObject("viewsTabBar"));
            } else {
                viewsTabBar = new DefaultViewsTabBar();
            }

            if (json.has("myViewsTabBar")) {
                myViewsTabBar = req.bindJSON(MyViewsTabBar.class,json.getJSONObject("myViewsTabBar"));
            } else {
                myViewsTabBar = new DefaultMyViewsTabBar();
            }

            primaryView = json.has("primaryView") ? json.getString("primaryView") : getViews().iterator().next().getViewName();

	    numExecutors = 0;

            quietPeriod = json.getInt("quiet_period");

            scmCheckoutRetryCount = json.getInt("retry_count");


            boolean result = true;
            for( Descriptor<?> d : Functions.getSortedDescriptorsForGlobalConfig() )
                result &= configureDescriptor(req,json,d);

            for( JSONObject o : StructuredForm.toList(json,"plugin"))
                pluginManager.getPlugin(o.getString("name")).getPlugin().configure(req, o);

            JSONObject np = json.getJSONObject("globalNodeProperties");
            if (!np.isNullObject()) {
                globalNodeProperties.rebuild(req, np, NodeProperty.for_(this));
            }

            version = VERSION;

            save();
            updateComputerList();
            if(result)
                rsp.sendRedirect(req.getContextPath()+'/');  // go to the top page
            else
                rsp.sendRedirect("configure"); // back to config
        } finally {
            bc.commit();
        }
    }

    /**
     * Gets the {@link CrumbIssuer} currently in use.
     *
     * @return null if none is in use.
     */
    public CrumbIssuer getCrumbIssuer() {
        return crumbIssuer;
    }

    public void setCrumbIssuer(CrumbIssuer issuer) {
        crumbIssuer = issuer;
    }

    public synchronized void doTestPost( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rsp.sendRedirect("foo");
    }

    private boolean configureDescriptor(StaplerRequest req, JSONObject json, Descriptor<?> d) throws FormException {
        // collapse the structure to remain backward compatible with the JSON structure before 1.
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject(); // if it doesn't have the property, the method returns invalid null object.
        json.putAll(js);
        return d.configure(req, js);
    }

    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigExecutorsSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(ADMINISTER);

        BulkChange bc = new BulkChange(this);
        try {
            JSONObject json = req.getSubmittedForm();

            setNumExecutors(Integer.parseInt(req.getParameter("numExecutors")));
            if(req.hasParameter("master.mode"))
                mode = Mode.valueOf(req.getParameter("master.mode"));
            else
                mode = Mode.NORMAL;

            setNodes(req.bindJSONToList(Slave.class,json.get("slaves")));
        } finally {
            bc.commit();
        }

        rsp.sendRedirect(req.getContextPath() + '/');  // go to the top page
    }

    /**
     * Accepts the new description.
     */
    public synchronized void doSubmitDescription( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        getPrimaryView().doSubmitDescription(req, rsp);
    }

    public synchronized HttpRedirect doQuietDown() throws IOException {
        try {
            return doQuietDown(false,0);
        } catch (InterruptedException e) {
            throw new AssertionError(); // impossible
        }
    }

    @CLIMethod(name="quiet-down")
    public HttpRedirect doQuietDown(
            @Option(name="-block",usage="Block until the system really quiets down and no builds are running") @QueryParameter boolean block,
            @Option(name="-timeout",usage="If non-zero, only block up to the specified number of milliseconds") @QueryParameter int timeout) throws InterruptedException, IOException {
        synchronized (this) {
            checkPermission(ADMINISTER);
            isQuietingDown = true;
        }
        if (block) {
            if (timeout > 0) timeout += System.currentTimeMillis();
            while (isQuietingDown
                   && (timeout <= 0 || System.currentTimeMillis() < timeout)
                   && !RestartListener.isAllReady()) {
                Thread.sleep(1000);
            }
        }
        return new HttpRedirect(".");
    }

    @CLIMethod(name="cancel-quiet-down")
    public synchronized HttpRedirect doCancelQuietDown() {
        checkPermission(ADMINISTER);
        isQuietingDown = false;
        getQueue().scheduleMaintenance();
        return new HttpRedirect(".");
    }

    /**
     * Backward compatibility. Redirect to the thread dump.
     */
    public void doClassicThreadDump(StaplerResponse rsp) throws IOException, ServletException {
        rsp.sendRedirect2("threadDump");
    }

    /**
     * Obtains the thread dump of all slaves (including the master.)
     *
     * <p>
     * Since this is for diagnostics, it has a built-in precautionary measure against hang slaves.
     */
    public Map<String,Map<String,String>> getAllThreadDumps() throws IOException, InterruptedException {
        checkPermission(ADMINISTER);

        // issue the requests all at once
        Map<String,Future<Map<String,String>>> future = new HashMap<String, Future<Map<String, String>>>();
        for (Computer c : getComputers()) {
            future.put(c.getName(), RemotingDiagnostics.getThreadDumpAsync(c.getChannel()));
        }

        // if the result isn't available in 5 sec, ignore that.
        // this is a precaution against hang nodes
        long endTime = System.currentTimeMillis() + 5000;

        Map<String,Map<String,String>> r = new HashMap<String, Map<String, String>>();
        for (Entry<String, Future<Map<String, String>>> e : future.entrySet()) {
            try {
                r.put(e.getKey(), e.getValue().get(endTime-System.currentTimeMillis(), TimeUnit.MILLISECONDS));
            } catch (Exception x) {
                StringWriter sw = new StringWriter();
                x.printStackTrace(new PrintWriter(sw,true));
                r.put(e.getKey(), Collections.singletonMap("Failed to retrieve thread dump",sw.toString()));
            }
        }
        return r;
    }

    public synchronized TopLevelItem doCreateItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        return itemGroupMixIn.createTopLevelItem(req, rsp);
    }

    /**
     * Creates a new job from its configuration XML. The type of the job created will be determined by
     * what's in this XML.
     * @since 1.319
     */
    public TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        return itemGroupMixIn.createProjectFromXML(name, xml);
    }

    /**
     * Copys a job.
     *
     * @param src
     *      A {@link TopLevelItem} to be copied.
     * @param name
     *      Name of the newly created project.
     * @return
     *      Newly created {@link TopLevelItem}.
     */
    @SuppressWarnings({"unchecked"})
    public <T extends TopLevelItem> T copy(T src, String name) throws IOException {
        return itemGroupMixIn.copy(src, name);
    }

    // a little more convenient overloading that assumes the caller gives us the right type
    // (or else it will fail with ClassCastException)
    public <T extends AbstractProject<?,?>> T copy(T src, String name) throws IOException {
        return (T)copy((TopLevelItem)src,name);
    }

    public synchronized void doCreateView( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        checkPermission(View.CREATE);
        addView(View.create(req,rsp, this));
    }

    /**
     * Check if the given name is suitable as a name
     * for job, view, etc.
     *
     * @throws ParseException
     *      if the given name is not good
     */
    public static void checkGoodName(String name) throws Failure {
        if(name==null || name.length()==0)
            throw new Failure(Messages.Hudson_NoName());

        for( int i=0; i<name.length(); i++ ) {
            char ch = name.charAt(i);
            if(Character.isISOControl(ch)) {
                throw new Failure(Messages.Hudson_ControlCodeNotAllowed(toPrintableName(name)));
            }
            if("?*/\\%!@#$^&|<>[]:;".indexOf(ch)!=-1)
                throw new Failure(Messages.Hudson_UnsafeChar(ch));
        }

        // looks good
    }

    /**
     * Makes sure that the given name is good as a job name.
     * @return trimmed name if valid; throws ParseException if not
     */
    private String checkJobName(String name) throws Failure {
        checkGoodName(name);
        name = name.trim();
        if(getItem(name)!=null)
            throw new Failure(Messages.Hudson_JobAlreadyExists(name));
        // looks good
        return name;
    }

    private static String toPrintableName(String name) {
        StringBuilder printableName = new StringBuilder();
        for( int i=0; i<name.length(); i++ ) {
            char ch = name.charAt(i);
            if(Character.isISOControl(ch))
                printableName.append("\\u").append((int)ch).append(';');
            else
                printableName.append(ch);
        }
        return printableName.toString();
    }

    /**
     * Checks if the user was successfully authenticated.
     *
     * @see BasicAuthenticationFilter
     */
    public void doSecured( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(req.getUserPrincipal()==null) {
            // authentication must have failed
            rsp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // the user is now authenticated, so send him back to the target
        String path = req.getContextPath()+req.getOriginalRestOfPath();
        String q = req.getQueryString();
        if(q!=null)
            path += '?'+q;

        rsp.sendRedirect2(path);
    }

    /**
     * Called once the user logs in. Just forward to the top page.
     */
    public void doLoginEntry( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        if(req.getUserPrincipal()==null) {
            rsp.sendRedirect2("noPrincipal");
            return;
        }

        String from = req.getParameter("from");
        if(from!=null && from.startsWith("/") && !from.equals("/loginError")) {
            rsp.sendRedirect2(from);    // I'm bit uncomfortable letting users redircted to other sites, make sure the URL falls into this domain
            return;
        }

        String url = AbstractProcessingFilter.obtainFullRequestUrl(req);
        if(url!=null) {
            // if the login redirect is initiated by Acegi
            // this should send the user back to where s/he was from.
            rsp.sendRedirect2(url);
            return;
        }

        rsp.sendRedirect2(".");
    }

    /**
     * Logs out the user.
     */
    public void doLogout( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        securityRealm.doLogout(req, rsp);
    }

    /**
     * Serves jar files for JNLP slave agents.
     */
    public Slave.JnlpJar getJnlpJars(String fileName) {
        return new Slave.JnlpJar(fileName);
    }

    public Slave.JnlpJar doJnlpJars(StaplerRequest req) {
        return new Slave.JnlpJar(req.getRestOfPath());
    }

    /**
     * Reloads the configuration.
     */
    @CLIMethod(name="reload-configuration")
    public synchronized HttpResponse doReload() throws IOException {
        checkPermission(ADMINISTER);

        // engage "loading ..." UI and then run the actual task in a separate thread
        servletContext.setAttribute("app", new HudsonIsLoading());

        new Thread("Hudson config reload thread") {
            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
                    reload();
                } catch (IOException e) {
                    LOGGER.log(SEVERE,"Failed to reload Hudson config",e);
                } catch (ReactorException e) {
                    LOGGER.log(SEVERE,"Failed to reload Hudson config",e);
                } catch (InterruptedException e) {
                    LOGGER.log(SEVERE,"Failed to reload Hudson config",e);
                }
            }
        }.start();

        return HttpResponses.redirectViaContextPath("/");
    }

    /**
     * Reloads the configuration synchronously.
     */
    public void reload() throws IOException, InterruptedException, ReactorException {
        executeReactor(null, loadTasks());
        User.reload();
        servletContext.setAttribute("app", this);
    }

    /**
     * Do a finger-print check.
     */
    public void doDoFingerprintCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // Parse the request
        MultipartFormDataParser p = new MultipartFormDataParser(req);
        if(isUseCrumbs() && !getCrumbIssuer().validateCrumb(req, p)) {
            rsp.sendError(HttpServletResponse.SC_FORBIDDEN,"No crumb found");
        }
        try {
            rsp.sendRedirect2(req.getContextPath()+"/fingerprint/"+
                Util.getDigestOf(p.getFileItem("name").getInputStream())+'/');
        } finally {
            p.cleanUp();
        }
    }

    /**
     * For debugging. Expose URL to perform GC.
     */
    public void doGc(StaplerResponse rsp) throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        System.gc();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("GCed");
    }

    /**
     * Obtains the heap dump.
     */
    public HeapDump getHeapDump() throws IOException {
        return new HeapDump(this,MasterComputer.localChannel);
    }

    /**
     * Simulates OutOfMemoryError.
     * Useful to make sure OutOfMemoryHeapDump setting.
     */
    public void doSimulateOutOfMemory() throws IOException {
        checkPermission(ADMINISTER);

        System.out.println("Creating artificial OutOfMemoryError situation");
        List<Object> args = new ArrayList<Object>();
        while (true)
            args.add(new byte[1024*1024]);
    }

    private transient final Map<UUID,FullDuplexHttpChannel> duplexChannels = new HashMap<UUID, FullDuplexHttpChannel>();

    /**
     * Handles HTTP requests for duplex channels for CLI.
     */
    public void doCli(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        if (!"POST".equals(req.getMethod())) {
            // for GET request, serve _cli.jelly, assuming this is a browser
            checkPermission(READ);
            req.getView(this,"_cli.jelly").forward(req,rsp);
            return;
        }

        // do not require any permission to establish a CLI connection
        // the actual authentication for the connecting Channel is done by CLICommand

        UUID uuid = UUID.fromString(req.getHeader("Session"));
        rsp.setHeader("Hudson-Duplex",""); // set the header so that the client would know

        FullDuplexHttpChannel server;
        if(req.getHeader("Side").equals("download")) {
            duplexChannels.put(uuid,server=new FullDuplexHttpChannel(uuid, !hasPermission(ADMINISTER)) {
                protected void main(Channel channel) throws IOException, InterruptedException {
                    // capture the identity given by the transport, since this can be useful for SecurityRealm.createCliAuthenticator()
                    channel.setProperty(CLICommand.TRANSPORT_AUTHENTICATION,getAuthentication());
                    channel.setProperty(CliEntryPoint.class.getName(),new CliManagerImpl(channel));
                }
            });
            try {
                server.download(req,rsp);
            } finally {
                duplexChannels.remove(uuid);
            }
        } else {
            duplexChannels.get(uuid).upload(req,rsp);
        }
    }

    /**
     * Binds /userContent/... to $JENKINS_HOME/userContent.
     */
    public DirectoryBrowserSupport doUserContent() {
        return new DirectoryBrowserSupport(this,getRootPath().child("userContent"),"User content","folder.png",true);
    }

    /**
     * Perform a restart of Hudson, if we can.
     *
     * This first replaces "app" to {@link HudsonIsRestarting}
     */
    @CLIMethod(name="restart")
    public void doRestart(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(ADMINISTER);
        if (req != null && req.getMethod().equals("GET")) {
            req.getView(this,"_restart.jelly").forward(req,rsp);
            return;
        }

        restart();

        if (rsp != null) // null for CLI
            rsp.sendRedirect2(".");
    }

    /**
     * Queues up a restart of Hudson for when there are no builds running, if we can.
     *
     * This first replaces "app" to {@link HudsonIsRestarting}
     *
     * @since 1.332
     */
    @CLIMethod(name="safe-restart")
    public HttpResponse doSafeRestart(StaplerRequest req) throws IOException, ServletException, RestartNotSupportedException {
        checkPermission(ADMINISTER);
        if (req != null && req.getMethod().equals("GET"))
            return HttpResponses.forwardToView(this,"_safeRestart.jelly");

        safeRestart();

        return HttpResponses.redirectToDot();
    }

    /**
     * Performs a restart.
     */
    public void restart() throws RestartNotSupportedException {
        final Lifecycle lifecycle = Lifecycle.get();
        lifecycle.verifyRestartable(); // verify that Hudson is restartable
        servletContext.setAttribute("app", new HudsonIsRestarting());

        new Thread("restart thread") {
            final String exitUser = getAuthentication().getName();
            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

                    // give some time for the browser to load the "reloading" page
                    Thread.sleep(5000);
                    LOGGER.severe(String.format("Restarting VM as requested by %s",exitUser));
                    for (RestartListener listener : RestartListener.all())
                        listener.onRestart();
                    lifecycle.restart();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Hudson",e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Hudson",e);
                }
            }
        }.start();
    }

    /**
     * Queues up a restart to be performed once there are no builds currently running.
     * @since 1.332
     */
    public void safeRestart() throws RestartNotSupportedException {
        final Lifecycle lifecycle = Lifecycle.get();
        lifecycle.verifyRestartable(); // verify that Hudson is restartable
        // Quiet down so that we won't launch new builds.
        isQuietingDown = true;

        new Thread("safe-restart thread") {
            final String exitUser = getAuthentication().getName();
            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

                    // Wait 'til we have no active executors.
                    doQuietDown(true, 0);

                    // Make sure isQuietingDown is still true.
                    if (isQuietingDown) {
                        servletContext.setAttribute("app",new HudsonIsRestarting());
                        // give some time for the browser to load the "reloading" page
                        LOGGER.info("Restart in 10 seconds");
                        Thread.sleep(10000);
                        LOGGER.severe(String.format("Restarting VM as requested by %s",exitUser));
                        for (RestartListener listener : RestartListener.all())
                            listener.onRestart();
                        lifecycle.restart();
                    } else {
                        LOGGER.info("Safe-restart mode cancelled");
                    }
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Hudson",e);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to restart Hudson",e);
                }
            }
        }.start();
    }

    /**
     * Shutdown the system.
     * @since 1.161
     */
    public void doExit( StaplerRequest req, StaplerResponse rsp ) throws IOException {
        checkPermission(ADMINISTER);
        LOGGER.severe(String.format("Shutting down VM as requested by %s from %s",
                getAuthentication().getName(), req.getRemoteAddr()));
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        PrintWriter w = rsp.getWriter();
        w.println("Shutting down");
        w.close();

        System.exit(0);
    }


    /**
     * Shutdown the system safely.
     * @since 1.332
     */
    public HttpResponse doSafeExit(StaplerRequest req) throws IOException {
        checkPermission(ADMINISTER);
        isQuietingDown = true;
        final String exitUser = getAuthentication().getName();
        final String exitAddr = req!=null ? req.getRemoteAddr() : "unknown";
        new Thread("safe-exit thread") {
            @Override
            public void run() {
                try {
                    SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
                    LOGGER.severe(String.format("Shutting down VM as requested by %s from %s",
                                                exitUser, exitAddr));
                    // Wait 'til we have no active executors.
                    while (isQuietingDown
                           && (overallLoad.computeTotalExecutors() > overallLoad.computeIdleExecutors())) {
                        Thread.sleep(5000);
                    }
                    // Make sure isQuietingDown is still true.
                    if (isQuietingDown) {
                        cleanUp();
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to shutdown Hudson",e);
                }
            }
        }.start();

        return HttpResponses.plainText("Shutting down as soon as all jobs are complete");
    }

    /**
     * Gets the {@link Authentication} object that represents the user
     * associated with the current request.
     */
    public static Authentication getAuthentication() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        // on Tomcat while serving the login page, this is null despite the fact
        // that we have filters. Looking at the stack trace, Tomcat doesn't seem to
        // run the request through filters when this is the login request.
        // see http://www.nabble.com/Matrix-authorization-problem-tp14602081p14886312.html
        if(a==null)
            a = ANONYMOUS;
        return a;
    }

    /**
     * For system diagnostics.
     * Run arbitrary Groovy script.
     */
    public void doScript(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doScript(req, rsp, req.getView(this, "_script.jelly"));
    }

    /**
     * Run arbitrary Groovy script and return result as plain text.
     */
    public void doScriptText(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        doScript(req, rsp, req.getView(this, "_scriptText.jelly"));
    }

    private void doScript(StaplerRequest req, StaplerResponse rsp, RequestDispatcher view) throws IOException, ServletException {
        // ability to run arbitrary script is dangerous
        checkPermission(EXECUTE_SCRIPT);

        String text = req.getParameter("script");
        if (text != null) {
            try {
                req.setAttribute("output",
                        RemotingDiagnostics.executeGroovy(text, MasterComputer.localChannel));
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }

        view.forward(req, rsp);
    }

    /**
     * Evaluates the Jelly script submitted by the client.
     *
     * This is useful for system administration as well as unit testing.
     */
    public void doEval(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(ADMINISTER);
        requirePOST();

        try {
            MetaClass mc = WebApp.getCurrent().getMetaClass(getClass());
            Script script = mc.classLoader.loadTearOff(JellyClassLoaderTearOff.class).createContext().compileScript(new InputSource(req.getReader()));
            new JellyRequestDispatcher(this,script).forward(req,rsp);
        } catch (JellyException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Sign up for the user account.
     */
    public void doSignup( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        req.getView(getSecurityRealm(), "signup.jelly").forward(req, rsp);
    }

    /**
     * Changes the icon size by changing the cookie
     */
    public void doIconSize( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        String qs = req.getQueryString();
        if(qs==null || !ICON_SIZE.matcher(qs).matches())
            throw new ServletException();
        Cookie cookie = new Cookie("iconSize", qs);
        cookie.setMaxAge(/* ~4 mo. */9999999); // #762
        rsp.addCookie(cookie);
        String ref = req.getHeader("Referer");
        if(ref==null)   ref=".";
        rsp.sendRedirect2(ref);
    }

    public void doFingerprintCleanup(StaplerResponse rsp) throws IOException {
        FingerprintCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    public void doWorkspaceCleanup(StaplerResponse rsp) throws IOException {
        WorkspaceCleanupThread.invoke();
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain");
        rsp.getWriter().println("Invoked");
    }

    /**
     * If the user chose the default JDK, make sure we got 'java' in PATH.
     */
    public FormValidation doDefaultJDKCheck(StaplerRequest request, @QueryParameter String value) {
        if(!value.equals("(Default)"))
            // assume the user configured named ones properly in system config ---
            // or else system config should have reported form field validation errors.
            return FormValidation.ok();

        // default JDK selected. Does such java really exist?
        if(JDK.isDefaultJDKValid(Jenkins.this))
            return FormValidation.ok();
        else
            return FormValidation.errorWithMarkup(Messages.Hudson_NoJavaInPath(request.getContextPath()));
    }

    /**
     * Makes sure that the given name is good as a job name.
     */
    public FormValidation doCheckJobName(@QueryParameter String value) {
        // this method can be used to check if a file exists anywhere in the file system,
        // so it should be protected.
        checkPermission(Item.CREATE);

        if(fixEmpty(value)==null)
            return FormValidation.ok();

        try {
            checkJobName(value);
            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * Checks if a top-level view with the given name exists.
     */
    public FormValidation doViewExistsCheck(@QueryParameter String value) {
        checkPermission(View.CREATE);

        String view = fixEmpty(value);
        if(view==null) return FormValidation.ok();

        if(getView(view)==null)
            return FormValidation.ok();
        else
            return FormValidation.error(Messages.Hudson_ViewAlreadyExists(view));
    }

    /**
     * Serves static resources placed along with Jelly view files.
     * <p>
     * This method can serve a lot of files, so care needs to be taken
     * to make this method secure. It's not clear to me what's the best
     * strategy here, though the current implementation is based on
     * file extensions.
     */
    public void doResources(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        // cut off the "..." portion of /resources/.../path/to/file
        // as this is only used to make path unique (which in turn
        // allows us to set a long expiration date
        path = path.substring(path.indexOf('/',1)+1);

        int idx = path.lastIndexOf('.');
        String extension = path.substring(idx+1);
        if(ALLOWED_RESOURCE_EXTENSIONS.contains(extension)) {
            URL url = pluginManager.uberClassLoader.getResource(path);
            if(url!=null) {
                long expires = MetaClass.NO_CACHE ? 0 : 365L * 24 * 60 * 60 * 1000; /*1 year*/
                rsp.serveFile(req,url,expires);
                return;
            }
        }
        rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Extension list that {@link #doResources(StaplerRequest, StaplerResponse)} can serve.
     * This set is mutable to allow plugins to add additional extensions.
     */
    public static final Set<String> ALLOWED_RESOURCE_EXTENSIONS = new HashSet<String>(Arrays.asList(
        "js|css|jpeg|jpg|png|gif|html|htm".split("\\|")
    ));

    /**
     * Checks if container uses UTF-8 to decode URLs. See
     * http://wiki.jenkins-ci.org/display/JENKINS/Tomcat#Tomcat-i18n
     */
    public FormValidation doCheckURIEncoding(StaplerRequest request) throws IOException {
        // expected is non-ASCII String
        final String expected = "\u57f7\u4e8b";
        final String value = fixEmpty(request.getParameter("value"));
        if (!expected.equals(value))
            return FormValidation.warningWithMarkup(Messages.Hudson_NotUsesUTF8ToDecodeURL());
        return FormValidation.ok();
    }

    /**
     * Does not check when system default encoding is "ISO-8859-1".
     */
    public static boolean isCheckURIEncodingEnabled() {
        return !"ISO-8859-1".equalsIgnoreCase(System.getProperty("file.encoding"));
    }

    /**
     * Rebuilds the dependency map.
     */
    public void rebuildDependencyGraph() {
        DependencyGraph graph = new DependencyGraph();
        graph.build();
        // volatile acts a as a memory barrier here and therefore guarantees 
        // that graph is fully build, before it's visible to other threads
        dependencyGraph = graph;
    }

    public DependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    // for Jelly
    public List<ManagementLink> getManagementLinks() {
        return ManagementLink.all();
    }

    /**
     * Exposes the current user to <tt>/me</tt> URL.
     */
    public User getMe() {
        User u = User.current();
        if (u == null)
            throw new AccessDeniedException("/me is not available when not logged in");
        return u;
    }

    /**
     * Gets the {@link Widget}s registered on this object.
     *
     * <p>
     * Plugins who wish to contribute boxes on the side panel can add widgets
     * by {@code getWidgets().add(new MyWidget())} from {@link Plugin#start()}.
     */
    public List<Widget> getWidgets() {
        return widgets;
    }

    public Object getTarget() {
        try {
            checkPermission(READ);
        } catch (AccessDeniedException e) {
            String rest = Stapler.getCurrentRequest().getRestOfPath();
            if(rest.startsWith("/login")
            || rest.startsWith("/logout")
            || rest.startsWith("/accessDenied")
            || rest.startsWith("/adjuncts/")
            || rest.startsWith("/signup")
            || rest.startsWith("/jnlpJars/")
            || rest.startsWith("/tcpSlaveAgentListener")
            || rest.startsWith("/cli")
            || rest.startsWith("/whoAmI")
            || rest.startsWith("/federatedLoginService/")
            || rest.startsWith("/securityRealm"))
                return this;    // URLs that are always visible without READ permission

            for (Action a : getActions()) {
                if (a instanceof UnprotectedRootAction) {
                    if (rest.startsWith("/"+a.getUrlName()+"/") || rest.equals("/"+a.getUrlName()))
                        return this;
                }
            }

            throw e;
        }
        return this;
    }

    /**
     * Fallback to the primary view.
     */
    public View getStaplerFallback() {
        return getPrimaryView();
    }

    public static class MasterComputer extends Computer {
        protected MasterComputer() {
            super(Jenkins.getInstance());
        }

        /**
         * Returns "" to match with {@link Jenkins#getNodeName()}.
         */
        @Override
        public String getName() {
            return "";
        }

        @Override
        public boolean isConnecting() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return Messages.Hudson_Computer_DisplayName();
        }

        @Override
        public String getCaption() {
            return Messages.Hudson_Computer_Caption();
        }

        @Override
        public String getUrl() {
            return "computer/(master)/";
        }

        public RetentionStrategy getRetentionStrategy() {
            return RetentionStrategy.NOOP;
        }

        /**
         * Report an error.
         */
        @Override
        public HttpResponse doDoDelete() throws IOException {
            throw HttpResponses.status(SC_BAD_REQUEST);
        }

        @Override
        public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // the master node isn't in the Hudson.getNodes(), so this method makes no sense.
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasPermission(Permission permission) {
            // no one should be allowed to delete the master.
            // this hides the "delete" link from the /computer/(master) page.
            if(permission==Computer.DELETE)
                return false;
            // Configuration of master node requires ADMINISTER permission
            return super.hasPermission(permission==Computer.CONFIGURE ? Jenkins.ADMINISTER : permission);
        }

        @Override
        public VirtualChannel getChannel() {
            return localChannel;
        }

        @Override
        public Charset getDefaultCharset() {
            return Charset.defaultCharset();
        }

        public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
            return logRecords;
        }

        public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this computer never returns null from channel, so
            // this method shall never be invoked.
            rsp.sendError(SC_NOT_FOUND);
        }

        /**
         * Redirect the master configuration to /configure.
         */
        public void doConfigure(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            rsp.sendRedirect2(req.getContextPath()+"/configure");
        }

        protected Future<?> _connect(boolean forceReconnect) {
            return Futures.precomputed(null);
        }

        /**
         * {@link LocalChannel} instance that can be used to execute programs locally.
         */
        public static final LocalChannel localChannel = new LocalChannel(threadPoolForRemoting);
    }

    /**
     * Shortcut for {@code Hudson.getInstance().lookup.get(type)}
     */
    public static <T> T lookup(Class<T> type) {
        return Jenkins.getInstance().lookup.get(type);
    }

    /**
     * Live view of recent {@link LogRecord}s produced by Hudson.
     */
    public static List<LogRecord> logRecords = Collections.emptyList(); // initialized to dummy value to avoid NPE

    /**
     * Thread-safe reusable {@link XStream}.
     */
    public static final XStream XSTREAM = new XStream2();

    /**
     * Alias to {@link #XSTREAM} so that one can access additional methods on {@link XStream2} more easily.
     */
    public static final XStream2 XSTREAM2 = (XStream2)XSTREAM;

    private static final int TWICE_CPU_NUM = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * Thread pool used to load configuration in parallel, to improve the start up time.
     * <p>
     * The idea here is to overlap the CPU and I/O, so we want more threads than CPU numbers.
     */
    /*package*/ transient final ExecutorService threadPoolForLoad = new ThreadPoolExecutor(
        TWICE_CPU_NUM, TWICE_CPU_NUM,
        5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());


    private static void computeVersion(ServletContext context) {
        // set the version
        Properties props = new Properties();
        try {
            InputStream is = Jenkins.class.getResourceAsStream("jenkins-version.properties");
            if(is!=null)
                props.load(is);
        } catch (IOException e) {
            e.printStackTrace(); // if the version properties is missing, that's OK.
        }
        String ver = props.getProperty("version");
        if(ver==null)   ver="?";
        VERSION = ver;
        context.setAttribute("version",ver);
        VERSION_HASH = Util.getDigestOf(ver).substring(0, 8);

        if(ver.equals("?") || Boolean.getBoolean("hudson.script.noCache"))
            RESOURCE_PATH = "";
        else
            RESOURCE_PATH = "/static/"+VERSION_HASH;

        VIEW_RESOURCE_PATH = "/resources/"+ VERSION_HASH;
    }

    /**
     * Version number of this Hudson.
     */
    public static String VERSION="?";

    /**
     * Parses {@link #VERSION} into {@link VersionNumber}, or null if it's not parseable as a version number
     * (such as when Hudson is run with "mvn hudson-dev:run")
     */
    public static VersionNumber getVersion() {
        try {
            return new VersionNumber(VERSION);
        } catch (NumberFormatException e) {
            try {
                // for non-released version of Hudson, this looks like "1.345 (private-foobar), so try to approximate.
                int idx = VERSION.indexOf(' ');
                if (idx>0)
                    return new VersionNumber(VERSION.substring(0,idx));
            } catch (NumberFormatException _) {
                // fall through
            }

            // totally unparseable
            return null;
        } catch (IllegalArgumentException e) {
            // totally unparseable
            return null;
        }
    }

    /**
     * Hash of {@link #VERSION}.
     */
    public static String VERSION_HASH;

    /**
     * Prefix to static resources like images and javascripts in the war file.
     * Either "" or strings like "/static/VERSION", which avoids Hudson to pick up
     * stale cache when the user upgrades to a different version.
     * <p>
     * Value computed in {@link WebAppMain}.
     */
    public static String RESOURCE_PATH = "";

    /**
     * Prefix to resources alongside view scripts.
     * Strings like "/resources/VERSION", which avoids Hudson to pick up
     * stale cache when the user upgrades to a different version.
     * <p>
     * Value computed in {@link WebAppMain}.
     */
    public static String VIEW_RESOURCE_PATH = "/resources/TBD";

    public static boolean PARALLEL_LOAD = Configuration.getBooleanConfigParameter("parallelLoad", true);
    public static boolean KILL_AFTER_LOAD = Configuration.getBooleanConfigParameter("killAfterLoad", false);
    public static boolean LOG_STARTUP_PERFORMANCE = Configuration.getBooleanConfigParameter("logStartupPerformance", false);
    private static final boolean CONSISTENT_HASH = true; // Boolean.getBoolean(Hudson.class.getName()+".consistentHash");
    /**
     * Enabled by default as of 1.337. Will keep it for a while just in case we have some serious problems.
     */
    public static boolean FLYWEIGHT_SUPPORT = Configuration.getBooleanConfigParameter("flyweightSupport", true);

    /**
     * Tentative switch to activate the concurrent build behavior.
     * When we merge this back to the trunk, this allows us to keep
     * this feature hidden for a while until we iron out the kinks.
     * @see AbstractProject#isConcurrentBuild()
     */
    public static boolean CONCURRENT_BUILD = true;

    /**
     * Switch to enable people to use a shorter workspace name.
     */
    private static final String WORKSPACE_DIRNAME = Configuration.getStringConfigParameter("workspaceDirName", "workspace");

    /**
     * Automatically try to launch a slave when Hudson is initialized or a new slave is created.
     */
    public static boolean AUTOMATIC_SLAVE_LAUNCH = true;

    private static final Logger LOGGER = Logger.getLogger(Jenkins.class.getName());

    private static final Pattern ICON_SIZE = Pattern.compile("\\d+x\\d+");

    public static final PermissionGroup PERMISSIONS = Permission.HUDSON_PERMISSIONS;
    public static final Permission ADMINISTER = Permission.HUDSON_ADMINISTER;
    public static final Permission READ = new Permission(PERMISSIONS,"Read",Messages._Hudson_ReadPermission_Description(),Permission.READ,PermissionScope.JENKINS);
    public static final Permission EXECUTE_SCRIPT = new Permission(PERMISSIONS, "ExecuteScript", Messages._Hudson_ExecuteScriptPermission_Description(),ADMINISTER,PermissionScope.JENKINS);

    /**
     * {@link Authentication} object that represents the anonymous user.
     * Because Acegi creates its own {@link AnonymousAuthenticationToken} instances, the code must not
     * expect the singleton semantics. This is just a convenient instance.
     *
     * @since 1.343
     */
    public static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
            "anonymous","anonymous",new GrantedAuthority[]{new GrantedAuthorityImpl("anonymous")});

    static {
        XSTREAM.alias("jenkins",Jenkins.class);
        XSTREAM.alias("slave", DumbSlave.class);
        XSTREAM.alias("jdk",JDK.class);
        // for backward compatibility with <1.75, recognize the tag name "view" as well.
        XSTREAM.alias("view", ListView.class);
        XSTREAM.alias("listView", ListView.class);
        // this seems to be necessary to force registration of converter early enough
        Mode.class.getEnumConstants();

        // double check that initialization order didn't do any harm
        assert PERMISSIONS!=null;
        assert ADMINISTER!=null;
    }
}

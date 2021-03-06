package data_objects;

import static data_objects.DataObjects.DATA_OBJECTS_MODULE_NAME;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.Java;
import org.jruby.javasupport.JavaObject;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

import data_objects.drivers.DriverDefinition;
import data_objects.util.JDBCUtil;
import org.jruby.runtime.callback.Callback;

/**
 * Connection Class.
 *
 * @author alexbcoles
 */
@SuppressWarnings("serial")
@JRubyClass(name = "Connection")
public final class Connection extends DORubyObject {

    public static final String RUBY_CLASS_NAME = "Connection";

    private static final String JNDI_PROTO = "jndi://";
    private static final String UTF8_ENCODING = "UTF-8";

    private static final ObjectAllocator CONNECTION_ALLOCATOR = new ObjectAllocator() {

        public IRubyObject allocate(final Ruby runtime, final RubyClass klass) {
            return new Connection(runtime, klass);
        }
    };

    public static RubyClass createConnectionClass(final Ruby runtime,
            final DriverDefinition driver) {
        RubyModule doModule = runtime.getModule(DATA_OBJECTS_MODULE_NAME);
        RubyClass superClass = doModule.getClass(RUBY_CLASS_NAME);
        RubyModule driverModule = (RubyModule) doModule.getConstant(driver
                .getModuleName());
        RubyClass connectionClass = driverModule.defineClassUnder(
                RUBY_CLASS_NAME, superClass, CONNECTION_ALLOCATOR);
        connectionClass.defineAnnotatedMethods(Connection.class);
        setDriverDefinition(connectionClass, runtime, driver);

        if (driver.supportsConnectionEncodings()) {
            connectionClass.defineFastMethod("character_set", new Callback() {
                public Arity getArity() {
                    return Arity.NO_ARGUMENTS;
                }
                public IRubyObject execute(final IRubyObject recv, final IRubyObject[] args, Block block) {
                    return recv.getInstanceVariables().fastGetInstanceVariable("@encoding");
                }
            });
        }
        return connectionClass;
    }

    private Connection(final Ruby runtime, final RubyClass klass) {
        super(runtime, klass);
    }

    // -------------------------------------------------- DATAOBJECTS PUBLIC API
    @JRubyMethod(required = 1)
    public IRubyObject initialize(final IRubyObject uri) {
        // System.out.println("============== initialize called " + uri);
        Ruby runtime = getRuntime();
        String jdbcDriver = null;
        String encoding = null;
        java.net.URI connectionUri;
        Map<String, String> query = null;

        try {
            connectionUri = driver.parseConnectionURI(uri);
        } catch (URISyntaxException ex) {
            throw runtime.newArgumentError("Malformed URI: " + ex);
            //Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            throw runtime.newArgumentError("Unsupported Encoding in Query Parameters" + ex);
        }

        // Normally, a database path must be specified. However, we should only
        // throw this error for opaque URIs - so URIs like jdbc:h2:mem should work.
        if (!connectionUri.isOpaque() && (connectionUri.getPath() == null
                || "".equals(connectionUri.getPath())
                || "/".equals(connectionUri.getPath()))) {
            throw runtime.newArgumentError("No database specified");
        }

        if (connectionUri.getQuery() != null) {
            try {
                query = parseQueryString(connectionUri.getQuery());
            } catch (UnsupportedEncodingException ex) {
                throw runtime.newArgumentError("Unsupported Encoding in Query Parameters" + ex);
            }

            jdbcDriver = query.get("driver");
            if (driver.supportsConnectionEncodings()) {
                encoding = query.get("encoding");
                if (encoding == null) {
                    encoding = query.get("charset");
                }
            }
        }

        if (driver.supportsConnectionEncodings()) {
            // default encoding to UTF-8, if not specified
            if (encoding == null) {
                encoding = UTF8_ENCODING;
            }
            api.setInstanceVariable(this, "@encoding", runtime.newString(encoding));
        }

        // Load JDBC Driver Class
        if (jdbcDriver != null) {
            try {
                Class.forName(jdbcDriver).newInstance();
            } catch (ClassNotFoundException cfe) {
                throw runtime.newArgumentError("Driver class library (" + jdbcDriver + ") not found.");
            } catch (InstantiationException ine) {
                throw runtime.newArgumentError("Driver class library you specified could not be instantiated");
            } catch (IllegalAccessException iae) {
                throw runtime.newArgumentError("Driver class library is not available:" + iae.getLocalizedMessage());
            }
            // should be handled implicitly
            // DriverManager.registerDriver(driver);
        }

        java.sql.Connection conn;

        try {
            if (connectionUri.getPath() != null && connectionUri.getPath().startsWith(JNDI_PROTO)) {
                String jndiName = connectionUri.getPath().substring(JNDI_PROTO.length());
                try {
                    InitialContext context = new InitialContext();
                    DataSource dataSource = (DataSource) context.lookup(jndiName);
                    // TODO maybe allow username and password here as well !??!
                    conn = dataSource.getConnection();
                } catch (NamingException ex) {
                    throw runtime.newRuntimeError("Can't lookup datasource: "
                                                  + connectionUri.toString() + "\n\t" + ex.getLocalizedMessage());
                }
            } else {
                String jdbcUri;
                Properties props = driver.getDefaultConnectionProperties();

                if (connectionUri.toString().contains("@")) {
                    // uri.getUserInfo() gave always null, so do it manually
                    // TODO: See if we can replace with connectionUri.getUserInfo()
                    String userInfo =
                            connectionUri.toString().replaceFirst(".*://", "").replaceFirst("@.*", "");
                    jdbcUri = connectionUri.toString().replaceFirst(userInfo + "@", "");
                    if (!userInfo.contains(":")) {
                        userInfo += ":";
                    }

                    // Replace . with : in scheme name - necessary for Oracle scheme oracle:thin
                    // : cannot be used in JDBC_URI_SCHEME as then it is identified as opaque URI
                    jdbcUri = jdbcUri.replaceFirst("^([a-z]+)(\\.)", "$1:");

                    if (!jdbcUri.startsWith("jdbc:")) {
                        jdbcUri = "jdbc:" + jdbcUri;
                    }
                    String username = userInfo.substring(0, userInfo.indexOf(":"));
                    String password = userInfo.substring(userInfo.indexOf(":") + 1);

                    props.put("user", username);
                    props.put("password", password);

                } else {
                    jdbcUri = connectionUri.toString();
                    if (!jdbcUri.startsWith("jdbc:")) {
                        jdbcUri = "jdbc:" + jdbcUri;
                    }
                }

                if (driver.supportsConnectionEncodings()) {
                    // we set encoding properties, and retry on failure
                    driver.setEncodingProperty(props, encoding);
                    conn = driver.getConnectionWithEncoding(runtime, this, jdbcUri, props);
                } else {
                    // if the driver does not use encoding, connect normally
                    conn = DriverManager.getConnection(jdbcUri, props);
                }
            }

        } catch (SQLException ex) {
            throw driver.newDriverError(runtime, "Can't connect: "
                                        + connectionUri.toString() + "\n\t" + ex.getLocalizedMessage());
        }

        // Callback for setting connection properties after connection is established
        try {
            driver.afterConnectionCallback(conn, query);
        } catch (SQLException ex) {
            throw driver.newDriverError(runtime, "Connection initialization error:"
                                        + "\n\t" + ex.getLocalizedMessage());
        }

        IRubyObject rubyconn = wrappedConnection(conn);

        api.setInstanceVariable(this, "@uri", uri);
        api.setInstanceVariable(this, "@connection", rubyconn);
        rubyconn.dataWrapStruct(conn);

        return runtime.getTrue();
    }

    @JRubyMethod
    public IRubyObject dispose() {
        // System.out.println("============== dispose called");
        Ruby runtime = getRuntime();
        IRubyObject connection = api.getInstanceVariable(this, "@connection");
        if (connection.isNil()) {
            return runtime.getFalse();
        }

        java.sql.Connection conn = getConnection(connection);
        if (conn == null) {
            return runtime.getFalse();
        }

        JDBCUtil.close(conn);

        api.setInstanceVariable(this, "@connection", runtime.getNil());
        return runtime.getTrue();
    }

    // ------------------------------------------------ ADDITIONAL JRUBY METHODS

    @JRubyMethod(required = 1)
    public IRubyObject quote_string(final IRubyObject value) {
        String quoted = driver.quoteString(value.asJavaString());
        return getRuntime().newString(quoted);
    }

    // -------------------------------------------------- PRIVATE HELPER METHODS
    private IRubyObject wrappedConnection(final java.sql.Connection c) {
        return Java.java_to_ruby(this, JavaObject.wrap(this.getRuntime(), c),
                Block.NULL_BLOCK);
    }

    private static java.sql.Connection getConnection(final IRubyObject recv) {
        return (java.sql.Connection) recv.dataGetStruct();
    }

    /**
     * Convert a query string (e.g.
     * driver=org.postgresql.Driver&protocol=postgresql) to a Map of values.
     *
     * @param query
     * @return
     */
    private static Map<String, String> parseQueryString(final String query)
            throws UnsupportedEncodingException {
        if (query == null) {
            return null;
        }
        Map<String, String> nameValuePairs = new HashMap<String, String>();
        StringTokenizer stz = new StringTokenizer(query, "&");

        // Tokenize at and for name / value pairs
        while (stz.hasMoreTokens()) {
            String nameValueToken = stz.nextToken();
            // Split at = to split the pairs
            int i = nameValueToken.indexOf("=");
            String name = nameValueToken.substring(0, i);
            String value = nameValueToken.substring(i + 1);
            // Name and value should be URL decoded
            name = java.net.URLDecoder.decode(name, "UTF-8");
            value = java.net.URLDecoder.decode(value, "UTF-8");
            nameValuePairs.put(name, value);
        }

        return nameValuePairs;
    }

}

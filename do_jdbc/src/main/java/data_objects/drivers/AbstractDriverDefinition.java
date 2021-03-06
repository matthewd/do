package data_objects.drivers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;
import java.util.Properties;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jruby.Ruby;
import org.jruby.RubyBigDecimal;
import org.jruby.RubyBignum;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyNumeric;
import org.jruby.RubyObjectAdapter;
import org.jruby.RubyProc;
import org.jruby.RubyRegexp;
import org.jruby.RubyString;
import org.jruby.RubyTime;
import org.jruby.exceptions.RaiseException;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ByteList;

import data_objects.RubyType;
import java.lang.UnsupportedOperationException;

/**
 *
 * @author alexbcoles
 * @author mkristian
 */
public abstract class AbstractDriverDefinition implements DriverDefinition {

    // assuming that API is thread safe
    protected static final RubyObjectAdapter API = JavaEmbedUtils
            .newObjectAdapter();

    protected final static DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    private final String scheme;
    private final String jdbcScheme;
    private final String moduleName;

    protected AbstractDriverDefinition(String scheme, String moduleName) {
        this(scheme, scheme, moduleName);
    }

    protected AbstractDriverDefinition(String scheme, String jdbcScheme,
            String moduleName) {
        this.scheme = scheme;
        this.jdbcScheme = jdbcScheme;
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public String getErrorName() {
        return this.moduleName + "Error";
    }

    @SuppressWarnings("unchecked")
    public URI parseConnectionURI(IRubyObject connection_uri)
            throws URISyntaxException, UnsupportedEncodingException {
        URI uri;

        if ("DataObjects::URI".equals(connection_uri.getType().getName())) {
            String query;
            StringBuffer userInfo = new StringBuffer();

            verifyScheme(stringOrNull(API.callMethod(connection_uri, "scheme")));

            String user = stringOrNull(API.callMethod(connection_uri, "user"));
            String password = stringOrNull(API.callMethod(connection_uri,
                    "password"));
            String host = stringOrNull(API.callMethod(connection_uri, "host"));
            int port = intOrMinusOne(API.callMethod(connection_uri, "port"));
            String path = stringOrNull(API.callMethod(connection_uri, "path"));
            IRubyObject query_values = API.callMethod(connection_uri, "query");
            String fragment = stringOrNull(API.callMethod(connection_uri,
                    "fragment"));

            if (user != null && !"".equals(user)) {
                userInfo.append(user);
                if (password != null && !"".equals(password)) {
                    userInfo.append(":").append(password);
                }
            }

            if (query_values.isNil()) {
                query = null;
            } else if (query_values instanceof RubyHash) {
                query = mapToQueryString(query_values.convertToHash());
            } else {
                query = API.callMethod(query_values, "to_s").asJavaString();
            }

            if (host != null && !"".equals(host)) {
                // a client/server database (e.g. MySQL, PostgreSQL, MS
                // SQLServer)
                uri = new URI(this.jdbcScheme, userInfo.toString(), host, port,
                        path, query, fragment);
            } else {
                // an embedded / file-based database (e.g. SQLite3, Derby
                // (embedded mode), HSQLDB - use opaque uri
                uri = new URI(this.jdbcScheme, path, fragment);
            }
        } else {
            // If connection_uri comes in as a string, we just pass it
            // through
            uri = new URI(connection_uri.asJavaString());
        }
        return uri;
    }

    protected void verifyScheme(String scheme) {
        if (!this.scheme.equals(scheme)) {
            throw new RuntimeException("scheme mismatch, expected: "
                    + this.scheme + " but got: " + scheme);
        }
    }

    /**
     * Convert a map of key/values to a URI query string
     *
     * @param map
     * @return
     * @throws java.io.UnsupportedEncodingException
     */
    private String mapToQueryString(Map<Object, Object> map)
            throws UnsupportedEncodingException {
        StringBuffer querySb = new StringBuffer();
        for (Map.Entry<Object, Object> pairs: map.entrySet()){
            String key = (pairs.getKey() != null) ? pairs.getKey().toString()
                    : "";
            String value = (pairs.getValue() != null) ? pairs.getValue()
                    .toString() : "";
            querySb.append(java.net.URLEncoder.encode(key, "UTF-8"))
                    .append("=");
            querySb.append(java.net.URLEncoder.encode(value, "UTF-8"));
        }
        return querySb.toString();
    }

    public RaiseException newDriverError(Ruby runtime, String message) {
        RubyClass driverError = runtime.getClass(getErrorName());
        return new RaiseException(runtime, driverError, message, true);
    }

    public RaiseException newDriverError(Ruby runtime, SQLException exception) {
        return newDriverError(runtime, exception, null);
    }

    public RaiseException newDriverError(Ruby runtime, SQLException exception,
            java.sql.Statement statement) {
        RubyClass driverError = runtime.getClass(getErrorName());
        int code = exception.getErrorCode();
        StringBuffer sb = new StringBuffer("(");

        // Append the Vendor Code, if there is one
        // TODO: parse vendor exception codes
        // TODO: replace 'vendor' with vendor name
        if (code > 0)
            sb.append("vendor_errno=").append(code).append(", ");
        sb.append("sql_state=").append(exception.getSQLState()).append(") ");
        sb.append(exception.getLocalizedMessage());

        if (statement != null)
            sb.append("\nQuery: ").append(statementToString(statement));

        return new RaiseException(runtime, driverError, sb.toString(), true);
    }

    public RubyObjectAdapter getObjectAdapter() {
        return API;
    }

    public RubyType jdbcTypeToRubyType(int type, int precision, int scale) {
        return RubyType.jdbcTypeToRubyType(type, scale);
    }

    public final IRubyObject getTypecastResultSetValue(Ruby runtime,
            ResultSet rs, int col, RubyType type) throws SQLException,
            IOException {
        // TODO assert to needs to be turned on with the java call
        // better throw something
        assert (type != null); // this method does not expect a null Ruby Type
        if (rs == null) {// || rs.wasNull()) {
            return runtime.getNil();
        }

        return doGetTypecastResultSetValue(runtime, rs, col, type);
    }

    protected IRubyObject doGetTypecastResultSetValue(Ruby runtime,
            ResultSet rs, int col, RubyType type) throws SQLException,
            IOException {
        //System.out.println(rs.getMetaData().getColumnTypeName(col) + " = " + type.toString());
        switch (type) {
        case FIXNUM:
        case INTEGER:
        case BIGNUM:
            try {
                // in most cases integers will fit into long type
                // and therefore should be faster to use getLong
                long lng = rs.getLong(col);
                if (rs.wasNull()) {
                    return runtime.getNil();
                }
                return RubyNumeric.int2fix(runtime, lng);
            } catch (SQLException sqle) {
                // if getLong failed then use getBigDecimal
                BigDecimal bdi = rs.getBigDecimal(col);
                if (bdi == null) {
                    return runtime.getNil();
                }
                // will return either Fixnum or Bignum
                return RubyBignum.bignorm(runtime, bdi.toBigInteger());
            }
        case FLOAT:
            // TODO: why getDouble is not used here?
            BigDecimal bdf = rs.getBigDecimal(col);
            if (bdf == null) {
                return runtime.getNil();
            }
            return new RubyFloat(runtime, bdf.doubleValue());
        case BIG_DECIMAL:
            BigDecimal bd = rs.getBigDecimal(col);
            if (bd  == null) {
                return runtime.getNil();
            }
            return new RubyBigDecimal(runtime, bd);
        case DATE:
            java.sql.Date date = rs.getDate(col);
            if (date == null) {
                return runtime.getNil();
            }
            return prepareRubyDateFromSqlDate(runtime, sqlDateToDateTime(date));
        case DATE_TIME:
            java.sql.Timestamp dt = null;
            // DateTimes with all-zero components throw a SQLException with
            // SQLState S1009 in MySQL Connector/J 3.1+
            // See
            // http://dev.mysql.com/doc/refman/5.0/en/connector-j-installing-upgrading.html
            try {
                dt = rs.getTimestamp(col);
            } catch (SQLException sqle) {
            }
            if (dt == null) {
                return runtime.getNil();
            }
            return prepareRubyDateTimeFromSqlTimestamp(runtime, sqlTimestampToDateTime(dt));
        case TIME:
            switch (rs.getMetaData().getColumnType(col)) {
            case Types.TIME:
                java.sql.Time tm = rs.getTime(col);
                if (tm == null) {
                    return runtime.getNil();
                }
                return prepareRubyTimeFromSqlTime(runtime, new DateTime(tm));
            case Types.TIMESTAMP:
                java.sql.Timestamp ts = rs.getTimestamp(col);
                if (ts == null) {
                    return runtime.getNil();
                }
                return prepareRubyTimeFromSqlTime(runtime, sqlTimestampToDateTime(ts));
            case Types.DATE:
                java.sql.Date da = rs.getDate(col);
                if (da == null) {
                    return runtime.getNil();
                }
                return prepareRubyTimeFromSqlDate(runtime, da);
            default:
                String str = rs.getString(col);
                if (str == null) {
                    return runtime.getNil();
                }
                RubyString return_str = RubyString.newUnicodeString(runtime,
                        str);
                return_str.setTaint(true);
                return return_str;
            }
        case TRUE_CLASS:
            // getBoolean delivers False in case the underlying data is null
            if (rs.getString(col) == null){
                return runtime.getNil();
            }
            return runtime.newBoolean(rs.getBoolean(col));
        case BYTE_ARRAY:
            InputStream binaryStream = rs.getBinaryStream(col);
            ByteList bytes = new ByteList(2048);
            try {
                byte[] buf = new byte[2048];
                for (int n = binaryStream.read(buf); n != -1; n = binaryStream
                        .read(buf)) {
                    bytes.append(buf, 0, n);
                }
            } finally {
                binaryStream.close();
            }
            return API.callMethod(runtime.fastGetModule("Extlib").fastGetClass(
                    "ByteArray"), "new", runtime.newString(bytes));
        case CLASS:
            String classNameStr = rs.getString(col);
            if (classNameStr == null) {
                return runtime.getNil();
            }
            RubyString class_name_str = RubyString.newUnicodeString(runtime, rs
                    .getString(col));
            class_name_str.setTaint(true);
            return API.callMethod(runtime.getObject(), "full_const_get",
                    class_name_str);
        case OBJECT:
            InputStream asciiStream = rs.getAsciiStream(col);
            IRubyObject obj = runtime.getNil();
            try {
                UnmarshalStream ums = new UnmarshalStream(runtime, asciiStream,
                        RubyProc.NEVER);
                obj = ums.unmarshalObject();
            } catch (IOException ioe) {
                // TODO: log this
            }
            return obj;
        case NIL:
            return runtime.getNil();
        case STRING:
        default:
            String str = rs.getString(col);
            if (str == null) {
                return runtime.getNil();
            }
            RubyString return_str = RubyString.newUnicodeString(runtime, str);
            return_str.setTaint(true);
            return return_str;
        }
    }

    public void setPreparedStatementParam(PreparedStatement ps,
            IRubyObject arg, int idx) throws SQLException {
        switch (RubyType.getRubyType(arg.getType().getName())) {
        case FIXNUM:
            ps.setInt(idx, Integer.parseInt(arg.toString()));
            break;
        case BIGNUM:
            ps.setLong(idx, ((RubyBignum) arg).getLongValue());
            break;
        case FLOAT:
            ps.setDouble(idx, RubyNumeric.num2dbl(arg));
            break;
        case BIG_DECIMAL:
            ps.setBigDecimal(idx, ((RubyBigDecimal) arg).getValue());
            break;
        case NIL:
            ps.setNull(idx, ps.getParameterMetaData().getParameterType(idx));
            break;
        case TRUE_CLASS:
        case FALSE_CLASS:
            ps.setBoolean(idx, arg.toString().equals("true"));
            break;
        case STRING:
            ps.setString(idx, arg.toString());
            break;
        case CLASS:
            ps.setString(idx, arg.toString());
            break;
        case BYTE_ARRAY:
            ps.setBytes(idx, ((RubyString) arg).getBytes());
            break;
        // TODO: add support for ps.setBlob();
        case DATE:
            ps.setDate(idx, java.sql.Date.valueOf(arg.toString()));
            break;
        case TIME:
            DateTime dateTime = ((RubyTime) arg).getDateTime();
            Timestamp ts = new Timestamp(dateTime.getMillis());
            ps.setTimestamp(idx, ts, dateTime.toGregorianCalendar());
            break;
        case DATE_TIME:
            String datetime = arg.toString().replace('T', ' ');
            ps.setTimestamp(idx, Timestamp.valueOf(datetime
                    .replaceFirst("[-+]..:..$", "")));
            break;
        case REGEXP:
            ps.setString(idx, ((RubyRegexp) arg).source().toString());
            break;
        // default case handling is simplified
        // TODO: if something is not working because of that then should be added to specs
        default:
            int jdbcType = ps.getParameterMetaData().getParameterType(idx);
            ps.setObject(idx, arg.toString(), jdbcType);
        }
    }

    public boolean registerPreparedStatementReturnParam(String sqlText, PreparedStatement ps, int idx) throws SQLException {
        return false;
    }

    public long getPreparedStatementReturnParam(PreparedStatement ps) throws SQLException {
        return 0;
    }

    public String prepareSqlTextForPs(String sqlText, IRubyObject[] args) {
        return sqlText;
    }

    public abstract boolean supportsJdbcGeneratedKeys();

    public abstract boolean supportsJdbcScrollableResultSets();

    public boolean supportsConnectionEncodings() {
        return false;
    }

    public boolean supportsConnectionPrepareStatementMethodWithGKFlag() {
        return true;
    }

    public ResultSet getGeneratedKeys(Connection connection) {
        return null;
    }

    public Properties getDefaultConnectionProperties() {
        return new Properties();
    }

    public void afterConnectionCallback(Connection connection, Map<String, String> query) throws SQLException {
        // do nothing
    }

    public void setEncodingProperty(Properties props, String encodingName) {
        // do nothing
    }

    public Connection getConnectionWithEncoding(Ruby runtime, IRubyObject connection,
            String url, Properties props) throws SQLException {
        throw new UnsupportedOperationException("This method only returns a method"
                + " for drivers that support specifiying an encoding.");
    }

    public String quoteString(String str) {
        StringBuffer quotedValue = new StringBuffer(str.length() + 2);
        quotedValue.append("\'");
        quotedValue.append(str.replaceAll("'", "''"));
        quotedValue.append("\'");
        return quotedValue.toString();
    }

    public String statementToString(Statement s) {
        return s.toString();
    }

    protected static DateTime sqlDateToDateTime(Date date) {
        date.getYear();
        if (date == null)
            return null;
        else
            return new DateTime(date.getYear()+1900, date.getMonth()+1, date.getDate(),
                        0, 0, 0, 0);
    }

    protected static DateTime sqlTimestampToDateTime(Timestamp ts) {
        if (ts == null)
            return null;
        return new DateTime(ts.getYear()+1900, ts.getMonth()+1, ts.getDate(),
                    ts.getHours(), ts.getMinutes(), ts.getSeconds(), ts.getNanos()/1000000);
    }

    protected static IRubyObject prepareRubyDateTimeFromSqlTimestamp(
            Ruby runtime, DateTime stamp) {

        if (stamp.getMillis() == 0) {
            return runtime.getNil();
        }

        int zoneOffset = stamp.getZone().getOffset(stamp.getMillis()) / 3600000;

        RubyClass klazz = runtime.fastGetClass("DateTime");

        IRubyObject rbOffset = runtime.fastGetClass("Rational").callMethod(
                runtime.getCurrentContext(),
                "new",
                new IRubyObject[] { runtime.newFixnum(zoneOffset),
                        runtime.newFixnum(24) });

        return klazz.callMethod(runtime.getCurrentContext(), "civil",
                new IRubyObject[] { runtime.newFixnum(stamp.getYear()),
                        runtime.newFixnum(stamp.getMonthOfYear()),
                        runtime.newFixnum(stamp.getDayOfMonth()),
                        runtime.newFixnum(stamp.getHourOfDay()),
                        runtime.newFixnum(stamp.getMinuteOfHour()),
                        runtime.newFixnum(stamp.getSecondOfMinute()),
                        rbOffset });
    }

    protected static IRubyObject prepareRubyTimeFromSqlTime(Ruby runtime,
            DateTime time) {
        // TODO: why in this case nil is returned?
        if (time.getMillis() + 3600000 == 0) {
            return runtime.getNil();
        }

        RubyTime rbTime = RubyTime.newTime(runtime, time);
        return rbTime;
    }

    protected static IRubyObject prepareRubyTimeFromSqlDate(Ruby runtime,
            Date date) {

        if (date.getTime() + 3600000 == 0) {
            return runtime.getNil();
        }
        RubyTime rbTime = RubyTime.newTime(runtime, date.getTime());
        return rbTime;
    }

    public static IRubyObject prepareRubyDateFromSqlDate(Ruby runtime,
            DateTime date) {

        if (date.getMillis() == 0) {
            return runtime.getNil();
        }

        RubyClass klazz = runtime.fastGetClass("Date");
        return klazz.callMethod(runtime.getCurrentContext(), "civil",
                new IRubyObject[] { runtime.newFixnum(date.getYear()),
                        runtime.newFixnum(date.getMonthOfYear()),
                        runtime.newFixnum(date.getDayOfMonth()) });
    }

    private static String stringOrNull(IRubyObject obj) {
        return (!obj.isNil()) ? obj.asJavaString() : null;
    }

    private static int intOrMinusOne(IRubyObject obj) {
        return (!obj.isNil()) ? RubyFixnum.fix2int(obj) : -1;
    }

    // private static Integer integerOrNull(IRubyObject obj) {
    // return (!obj.isNil()) ? RubyFixnum.fix2int(obj) : null;
    // }
}

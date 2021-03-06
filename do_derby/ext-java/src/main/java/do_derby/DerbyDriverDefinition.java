package do_derby;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jruby.Ruby;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

import data_objects.RubyType;
import data_objects.drivers.AbstractDriverDefinition;

public class DerbyDriverDefinition extends AbstractDriverDefinition {
    public final static String URI_SCHEME = "derby";
    public final static String RUBY_MODULE_NAME = "Derby";

    public DerbyDriverDefinition() {
        super(URI_SCHEME, RUBY_MODULE_NAME);
    }

    @Override
    protected IRubyObject doGetTypecastResultSetValue(Ruby runtime,
            ResultSet rs, int col, RubyType type) throws SQLException,
            IOException {
        switch (type) {
        case BYTE_ARRAY:
            InputStream binaryStream = rs.getBinaryStream(col);
            // TODO there are NullPointerExceptions without that.
            // returning NIL fixes the spec but don't know why
            // it runs through BYTE_ARRAY
            if(binaryStream == null){
                return runtime.getNil();
            }
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
        default:
            return super.doGetTypecastResultSetValue(runtime, rs, col, type);
        }
    }

    @Override
    public boolean supportsJdbcGeneratedKeys()
    {
        return true;
    }

    @Override
    public boolean supportsJdbcScrollableResultSets() {
        return true;
    }
}

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;

@TargetClass(BasicAuthCache.class)
final class Target_org_apache_http_impl_client_BasicAuthCache {
    @Substitute
    public AuthScheme get(final HttpHost host) {
        return new BasicScheme();
    }

    @Substitute
    public void put(final HttpHost host, final AuthScheme authScheme) {

    }
}
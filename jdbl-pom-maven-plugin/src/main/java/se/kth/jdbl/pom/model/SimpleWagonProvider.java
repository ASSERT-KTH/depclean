package se.kth.jdbl.pom.model;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.apache.maven.wagon.providers.http.LightweightHttpsWagon;
import org.sonatype.aether.connector.wagon.WagonProvider;

/**
 * Simple HTTP and HTTPS Wagon Provider.
 */
public class SimpleWagonProvider implements WagonProvider {

    @Override
    public Wagon lookup(String roleHint) throws Exception {
        if (roleHint.equalsIgnoreCase("http")) {
            return new LightweightHttpWagon();
        } else if (roleHint.equalsIgnoreCase("https")) {
            return new LightweightHttpsWagon();
        }
        return null;
    }

    @Override
    public void release(Wagon wagon) {
        //nop
    }
}

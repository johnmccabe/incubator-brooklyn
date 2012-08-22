package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Preconditions

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void shutdown() {
        app?.stop()
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testWhenNoServersReturns404() {
        def serverFactory = { throw new UnsupportedOperationException(); }
        cluster = new DynamicCluster(owner:app, factory:serverFactory, initialSize:0)
        
        nginx = new NginxController([
                "owner" : app,
                "cluster" : cluster,
                "domain" : "localhost",
                "port" : 8000
            ])
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        executeUntilSucceeds() {
            // Nginx has started
            assertTrue nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)

            // Nginx URL is available
            String url = nginx.getAttribute(NginxController.ROOT_URL)
            assertEquals(urlRespondsStatusCode(url), 404);
        }
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testCanStartupAndShutdown() {
        def template = { Map properties -> new JBoss7Server(properties) }
        URL war = getClass().getClassLoader().getResource("hello-world.war")
        Preconditions.checkState war != null, "Unable to locate resource $war"
        
        cluster = new DynamicCluster(owner:app, factory:template, initialSize:1, httpPort:7080)
        cluster.setConfig(JavaWebAppService.ROOT_WAR, war.path)
        
        nginx = new NginxController([
	            "owner" : app,
	            "cluster" : cluster,
	            "domain" : "localhost",
	            "port" : 8000,
	            "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        executeUntilSucceeds() {
            // Services are running
            assertTrue cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP)
            cluster.members.each { assertTrue it.getAttribute(SoftwareProcessEntity.SERVICE_UP) }
            
            assertTrue nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)

            // Nginx URL is available
            String url = nginx.getAttribute(NginxController.ROOT_URL)
            assertTrue urlRespondsWithStatusCode200(url)

            // Web-server URL is available
            cluster.members.each {
	            assertTrue urlRespondsWithStatusCode200(it.getAttribute(WebAppService.ROOT_URL))
            }
        }
        
        app.stop()

        // Services have stopped
        assertFalse nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        assertFalse cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        cluster.members.each { assertFalse it.getAttribute(SoftwareProcessEntity.SERVICE_UP) }
    }
}

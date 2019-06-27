package amino.run.compiler;

import amino.run.sampleSO.SO;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

public class AppStubTest {

    @Test
    public void testAppStubNonStaticPublicMethod() throws Exception {
        AppStub appStub = new AppStub(SO.class);

        TreeSet<Stub.MethodStub> stubMethods = appStub.getMethods();
        for (Stub.MethodStub methodStub : stubMethods) {
            // Assert to check for static method generation in App stub methods set.
            Assert.assertNotEquals(methodStub.getName(), "staticMethod");
        }
    }
}
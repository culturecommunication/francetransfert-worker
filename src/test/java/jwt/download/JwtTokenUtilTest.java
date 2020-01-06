package jwt.download;

import fr.gouv.culture.francetransfert.FranceTransfertWorkerStarter;
import fr.gouv.culture.francetransfert.security.JwtRequest;
import fr.gouv.culture.francetransfert.security.JwtTokenUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FranceTransfertWorkerStarter.class)
public class JwtTokenUtilTest {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void createTokenFranceTransfert() throws Exception {
        //given
        String enclosureId = "8ffd72f0-4432-4e07-b247-362b1eb4edfb";
        String mailRecipient = "louay@live.fr";
        boolean withPassword = false;
        JwtRequest jwtRequest = new JwtRequest(enclosureId, mailRecipient, withPassword);
        //when
        String token = jwtTokenUtil.generateTokenDownload(jwtRequest);
        //then
        Assert.assertNotNull(token);
    }

    @After
    public void tearDown() throws Exception {

    }
}

package uk.gov.hmcts.reform.blobrouter.util;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

final class SigningHelper {

    static byte[] signWithSha256Rsa(byte[] input, byte[] keyBytes) throws Exception {

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes)));
        signature.update(input);
        return signature.sign();
    }

    private SigningHelper() {
        // util class
    }
}
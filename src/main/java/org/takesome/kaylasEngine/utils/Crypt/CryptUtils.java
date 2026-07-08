package org.takesome.kaylasEngine.utils.Crypt;

import org.takesome.kaylasEngine.Engine;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CryptUtils {

    private final CryptHelper cryptHelper;

    public CryptUtils() {
        this.cryptHelper = new CryptHelper();
    }

    public String decrypt(String input, String key) {
        byte[] output = null;
        try {
            SecretKeySpec skey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(2, skey);
            output = cipher.doFinal(cryptHelper.getDecoder().decode(input));
        } catch (InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            Engine.getLOGGER().error("Key is not valid for: " + input);
            return null;
        }
        assert output != null;
        return new String(output);
    }

    public String encrypt(String input, String key) {
        byte[] crypted = null;
        try {
            SecretKeySpec skey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(1, skey);
            crypted = cipher.doFinal(input.getBytes());
        } catch (InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            Engine.getLOGGER().error("Key must be 16 symbols!");
        }
        assert crypted != null;
        return new String(cryptHelper.getEncoder().encode(crypted));
    }


}

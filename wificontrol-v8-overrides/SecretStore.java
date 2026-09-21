package org.sayeh.wificontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecretStore {
    private static final String ALIAS="wifi_control_v8_router_secret";
    private static final String PREF="wifi_control_v8_secret";
    private SecretStore(){}

    public static void save(Context c,String secret) throws Exception {
        SecretKey key=key();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] ct=cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .putString("ct",Base64.encodeToString(ct,Base64.NO_WRAP)).apply();
    }

    public static String load(Context c) throws Exception {
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String iv=p.getString("iv",""); String ct=p.getString("ct",""); if(iv.isEmpty()||ct.isEmpty()) return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(ct,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        KeyStore.Entry e=ks.getEntry(ALIAS,null); if(e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry)e).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
}

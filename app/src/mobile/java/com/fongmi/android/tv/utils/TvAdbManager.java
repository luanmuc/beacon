package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Build;
import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/**
 * 手机端 ADB 客户端：用于给电视安装/更新 APK。
 * 密钥与证书只保存在本机 filesDir，配对一次后复用。
 */
public class TvAdbManager extends AbsAdbConnectionManager {

    private static volatile AbsAdbConnectionManager instance;

    public static AbsAdbConnectionManager get(@NonNull Context context) throws Exception {
        if (instance == null) {
            synchronized (TvAdbManager.class) {
                if (instance == null) instance = new TvAdbManager(context.getApplicationContext());
            }
        }
        return instance;
    }

    private PrivateKey privateKey;
    private Certificate certificate;

    private TvAdbManager(@NonNull Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        privateKey = readPrivateKey(context);
        certificate = readCertificate(context);
        if (privateKey == null || certificate == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair pair = generator.generateKeyPair();
            PublicKey publicKey = pair.getPublic();
            PrivateKey key = pair.getPrivate();
            String algorithm = "SHA512withRSA";
            CertificateExtensions extensions = new CertificateExtensions();
            extensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
            X500Name name = new X500Name("CN=Beacon TV Install");
            Date notBefore = new Date();
            Date notAfter = new Date(System.currentTimeMillis() + 86400000L);
            extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));
            CertificateValidity validity = new CertificateValidity(notBefore, notAfter);
            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
            info.set("subject", new CertificateSubjectName(name));
            info.set("key", new CertificateX509Key(publicKey));
            info.set("validity", validity);
            info.set("issuer", new CertificateIssuerName(name));
            info.set("extensions", extensions);
            X509CertImpl impl = new X509CertImpl(info);
            impl.sign(key, algorithm);
            privateKey = key;
            certificate = impl;
            writePrivateKey(context, key);
            writeCertificate(context, impl);
        }
    }

    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return "BeaconTV";
    }

    @Nullable
    private static Certificate readCertificate(Context context) throws IOException, CertificateException {
        File file = new File(context.getFilesDir(), "tv_adb_cert.pem");
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private static void writeCertificate(Context context, Certificate certificate) throws CertificateEncodingException, IOException {
        File file = new File(context.getFilesDir(), "tv_adb_cert.pem");
        BASE64Encoder encoder = new BASE64Encoder();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            encoder.encode(certificate.getEncoded(), out);
            out.write('\n');
            out.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nullable
    private static PrivateKey readPrivateKey(Context context) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        File file = new File(context.getFilesDir(), "tv_adb_key.bin");
        if (!file.exists()) return null;
        byte[] bytes = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            int read = in.read(bytes);
            if (read <= 0) return null;
        }
        EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static void writePrivateKey(Context context, PrivateKey key) throws IOException {
        try (OutputStream out = new FileOutputStream(new File(context.getFilesDir(), "tv_adb_key.bin"))) {
            out.write(key.getEncoded());
        }
    }
}

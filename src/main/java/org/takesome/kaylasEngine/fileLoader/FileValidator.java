package org.takesome.kaylasEngine.fileLoader;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * Реализация валидатора файлов, отвечающая за проверку корректности локального файла.
 * Данный класс реализует интерфейс IFileValidator, что позволяет легко заменить его на другую реализацию при необходимости.
 */
public class FileValidator implements IFileValidator {

    /**
     * Проверяет, является ли локальный файл недействительным по сравнению с ожидаемыми параметрами.
     *
     * @param file         локальный файл, который требуется проверить
     * @param expectedHash ожидаемый MD5 хэш файла
     * @param expectedSize ожидаемый размер файла в байтах
     * @return true, если файл не существует, его размер не совпадает с ожидаемым, либо хэш не совпадает; иначе false
     */
    @Override
    public boolean isInvalidFile(File file, String expectedHash, long expectedSize) {
        if (!file.exists() || file.length() != expectedSize) {
            return true;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dataBytes = new byte[1024];
            int bytesRead;

            try (FileInputStream fis = new FileInputStream(file)) {
                while ((bytesRead = fis.read(dataBytes)) != -1) {
                    md.update(dataBytes, 0, bytesRead);
                }
            }

            byte[] digestBytes = md.digest();
            StringBuilder hexString = new StringBuilder();

            for (byte digestByte : digestBytes) {
                hexString.append(String.format("%02x", digestByte));
            }

            return !hexString.toString().equals(expectedHash);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
}

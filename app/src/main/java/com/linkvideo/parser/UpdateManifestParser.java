package com.linkvideo.parser;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Locale;

final class UpdateManifestParser {
    private UpdateManifestParser() { }

    static UpdateManifest parse(String json) {
        if (json == null || json.trim().isEmpty()) throw invalid("更新清单为空", null);
        try {
            JSONObject object = new JSONObject(json);
            int schemaVersion = requiredInt(object, "schemaVersion");
            String packageName = requiredString(object, "packageName");
            long versionCode = requiredLong(object, "versionCode");
            String versionName = requiredString(object, "versionName");
            int minSdk = requiredInt(object, "minSdk");
            String apkUrl = requiredString(object, "apkUrl");
            long sizeBytes = requiredLong(object, "sizeBytes");
            String sha256 = requiredString(object, "sha256").toLowerCase(Locale.ROOT);
            String releaseNotes = requiredString(object, "releaseNotes");

            if (schemaVersion != 1) throw invalid("不支持的更新清单版本", null);
            if (packageName.isEmpty()) throw invalid("更新清单缺少包名", null);
            if (versionCode <= 0 || versionName.isEmpty() || minSdk <= 0 || sizeBytes < 0) {
                throw invalid("更新清单数值无效", null);
            }
            URI uri = URI.create(apkUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw invalid("安装包地址必须使用 HTTPS", null);
            }
            if (!sha256.matches("[0-9a-f]{64}")) throw invalid("SHA-256 格式错误", null);
            return new UpdateManifest(schemaVersion, packageName, versionCode, versionName,
                    minSdk, apkUrl, sizeBytes, sha256, releaseNotes, json);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("更新清单")) {
                throw exception;
            }
            throw invalid("更新清单格式错误", exception);
        } catch (JSONException exception) {
            throw invalid("更新清单格式错误", exception);
        }
    }

    private static String requiredString(JSONObject object, String name) throws JSONException {
        Object value = required(object, name);
        if (!(value instanceof String)) throw invalid("更新清单字段类型错误：" + name, null);
        return ((String) value).trim();
    }

    private static int requiredInt(JSONObject object, String name) throws JSONException {
        long value = requiredLong(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid("更新清单字段超出范围：" + name, null);
        }
        return (int) value;
    }

    private static long requiredLong(JSONObject object, String name) throws JSONException {
        Object value = required(object, name);
        if (!(value instanceof Number) || value instanceof Double || value instanceof Float) {
            throw invalid("更新清单字段类型错误：" + name, null);
        }
        return ((Number) value).longValue();
    }

    private static Object required(JSONObject object, String name) throws JSONException {
        if (!object.has(name) || object.isNull(name)) throw invalid("更新清单缺少字段：" + name, null);
        return object.get(name);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return cause == null ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}

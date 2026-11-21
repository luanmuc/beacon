package com.fongmi.android.tv.player;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

import com.fongmi.android.tv.player.exo.ErrorMsgProvider;

/**
 * Normalized playback failure. Activities must branch on type/status, never
 * on localized error text. The message is kept for display only.
 */
public final class PlaybackFailure {

    public enum Type {
        NETWORK,
        HTTP,
        DECODE,
        DRM,
        FORMAT,
        UNKNOWN
    }

    private static final ErrorMsgProvider MESSAGES = new ErrorMsgProvider();

    private final Type type;
    private final int errorCode;
    private final int httpStatus;
    private final String message;

    private PlaybackFailure(Type type, int errorCode, int httpStatus, String message) {
        this.type = type;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public static PlaybackFailure from(PlaybackException error) {
        int status = findHttpStatus(error);
        String message = MESSAGES.get(error);
        if (message == null) message = error.getErrorCodeName();
        if (status > 0 && !message.contains(String.valueOf(status))) message = message + " (HTTP " + status + ")";
        return new PlaybackFailure(typeOf(error.errorCode), error.errorCode, status, message);
    }

    public Type getType() {
        return type;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public boolean isHttp4xx() {
        return type == Type.HTTP && httpStatus >= 400 && httpStatus < 500;
    }

    public boolean isDecode() {
        return type == Type.DECODE;
    }

    public boolean isDrm() {
        return type == Type.DRM;
    }

    private static Type typeOf(int errorCode) {
        return switch (errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    PlaybackException.ERROR_CODE_TIMEOUT -> Type.NETWORK;
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> Type.HTTP;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> Type.DECODE;
            case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                    PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                    PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> Type.DRM;
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> Type.FORMAT;
            default -> Type.UNKNOWN;
        };
    }

    private static int findHttpStatus(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException response) return response.responseCode;
            cause = cause.getCause();
        }
        return 0;
    }
}

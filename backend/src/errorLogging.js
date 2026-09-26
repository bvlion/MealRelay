'use strict';

function safeScalar(value) {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
    ? value
    : undefined;
}

function addField(target, key, value) {
  if (value !== undefined) target[key] = value;
}

function prefixed(prefix, key) {
  return prefix ? `${prefix}${key[0].toUpperCase()}${key.slice(1)}` : key;
}

function errorMetadata(error, prefix = '') {
  const metadata = {};
  const key = (name) => prefixed(prefix, name);
  addField(metadata, key('errorName'), typeof error?.name === 'string' ? error.name : undefined);
  addField(metadata, key('status'), Number.isInteger(error?.status) ? error.status : undefined);
  addField(metadata, key('errorCode'), safeScalar(error?.code));
  addField(metadata, key('errorType'), safeScalar(error?.type));
  addField(metadata, key('requestId'), safeScalar(error?.request_id));

  const response = error?.response;
  addField(metadata, key('upstreamStatus'), Number.isInteger(response?.status) ? response.status : undefined);
  addField(metadata, key('upstreamErrorSubtype'), safeScalar(response?.data?.error_subtype));
  const upstreamError = response?.data?.error;
  if (typeof upstreamError === 'string') {
    addField(metadata, key('upstreamError'), upstreamError);
  } else if (upstreamError && typeof upstreamError === 'object') {
    addField(metadata, key('upstreamErrorCode'), safeScalar(upstreamError.code));
    addField(metadata, key('upstreamErrorType'), safeScalar(upstreamError.type));
  }
  return metadata;
}

function logBackendError({ message, error, responseStatus, reason, severity = 'ERROR' }) {
  const entry = { severity, message };
  addField(entry, 'responseStatus', responseStatus);
  addField(entry, 'reason', reason);
  Object.assign(entry, errorMetadata(error));
  if (error?.cause && error.cause !== error) {
    Object.assign(entry, errorMetadata(error.cause, 'cause'));
  }

  const output = JSON.stringify(entry);
  if (severity === 'WARNING') {
    console.warn(output);
  } else {
    console.error(output);
  }
}

module.exports = { logBackendError };

'use strict';

class InvalidFirebaseInstallationRequestError extends Error {}

function parseFirebaseInstallationRequest(body) {
  const fid = body?.fid;
  if (typeof fid !== 'string' || !fid.trim()) {
    throw new InvalidFirebaseInstallationRequestError('Firebase Installation ID is invalid');
  }
  return { fid };
}

module.exports = { InvalidFirebaseInstallationRequestError, parseFirebaseInstallationRequest };

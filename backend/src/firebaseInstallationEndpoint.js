'use strict';

const { AuthenticationError } = require('./errors');
const { logBackendError } = require('./errorLogging');
const { authenticateMealRelayRequest } = require('./apiAuthentication');
const {
  InvalidFirebaseInstallationRequestError,
  parseFirebaseInstallationRequest,
} = require('./firebaseInstallationRequest');

async function handleFirebaseInstallationRequest({ request, response, authRepository, installationRepository }) {
  response.set('Cache-Control', 'no-store');
  if (request.method !== 'PUT') {
    response.status(405).end();
    return;
  }
  if (!request.is('application/json')) {
    response.status(415).json({ error: 'JSON is required' });
    return;
  }
  try {
    const sub = await authenticateMealRelayRequest({
      authorization: request.get('authorization'), repository: authRepository,
    });
    const { fid } = parseFirebaseInstallationRequest(request.body);
    await installationRepository.register({ sub, fid });
    response.status(204).end();
  } catch (error) {
    const isHandled = error instanceof InvalidFirebaseInstallationRequestError ||
      error instanceof AuthenticationError;
    const responseStatus = error instanceof InvalidFirebaseInstallationRequestError
      ? 400
      : error instanceof AuthenticationError ? error.status : 500;
    logBackendError({
      message: 'Firebase installation request failed',
      error,
      responseStatus,
      reason: isHandled ? error.message : undefined,
      severity: responseStatus >= 500 ? 'ERROR' : 'WARNING',
    });
    if (isHandled) {
      response.status(responseStatus).json({ error: error.message });
      return;
    }
    response.status(500).json({ error: 'Firebase installation could not be registered' });
  }
}

module.exports = { handleFirebaseInstallationRequest };

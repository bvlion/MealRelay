'use strict';

const { AuthenticationError } = require('./errors');
const { authenticateMealRelayRequest } = require('./apiAuthentication');

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
    const { fid, previousFid } = request.body ?? {};
    if (typeof fid !== 'string' || !fid.trim() ||
        (previousFid !== undefined && (typeof previousFid !== 'string' || !previousFid.trim()))) {
      response.status(400).json({ error: 'Firebase Installation ID is invalid' });
      return;
    }
    await installationRepository.register({ sub, fid, previousFid });
    response.status(204).end();
  } catch (error) {
    if (error instanceof AuthenticationError) {
      response.status(error.status).json({ error: error.message });
      return;
    }
    response.status(500).json({ error: 'Firebase installation could not be registered' });
  }
}

module.exports = { handleFirebaseInstallationRequest };

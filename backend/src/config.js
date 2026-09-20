'use strict';

function loadAuthenticationConfig(environment) {
  const allowedEmails = JSON.parse(environment.MEAL_RELAY_ALLOWED_EMAILS || 'null');
  const clientId = environment.GOOGLE_OAUTH_CLIENT_ID;
  const clientSecret = environment.GOOGLE_OAUTH_CLIENT_SECRET;
  if (!Array.isArray(allowedEmails) || allowedEmails.length !== 2 ||
      allowedEmails.some((email) => typeof email !== 'string') || !clientId || !clientSecret) {
    throw new Error('Authentication configuration is incomplete');
  }
  const normalizedEmails = new Set(allowedEmails.map((email) => email.toLowerCase()));
  if (normalizedEmails.size !== 2) throw new Error('Authentication configuration is incomplete');
  return { allowedEmails: normalizedEmails, clientId, clientSecret };
}

module.exports = { loadAuthenticationConfig };

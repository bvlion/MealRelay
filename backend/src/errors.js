'use strict';

class AuthenticationError extends Error {
  constructor(message, status = 401, options) {
    super(message, options);
    this.status = status;
  }
}

module.exports = { AuthenticationError };

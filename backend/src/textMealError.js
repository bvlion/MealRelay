'use strict';

class TextMealError extends Error {
  constructor(message, status = 400, options) {
    super(message, options);
    this.status = status;
  }
}

module.exports = { TextMealError };

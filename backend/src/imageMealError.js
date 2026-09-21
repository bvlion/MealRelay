'use strict';

class ImageMealError extends Error {
  constructor(message, status = 400, options) {
    super(message, options);
    this.status = status;
  }
}

module.exports = { ImageMealError };

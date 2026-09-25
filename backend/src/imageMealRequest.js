'use strict';

const Busboy = require('busboy');
const { ImageMealError } = require('./imageMealError');

const SUPPORTED_IMAGE_MEDIA_TYPES = new Set([
  'image/gif',
  'image/jpeg',
  'image/png',
  'image/webp',
]);
const REQUEST_FIELDS = new Set(['capturedAt', 'mealId']);

function createMultipartParser(request) {
  try {
    return Busboy({
      headers: request.headers,
      limits: {
        fields: REQUEST_FIELDS.size,
      },
    });
  } catch {
    throw new ImageMealError('Multipart request is invalid');
  }
}

function parseImageMealRequest(request) {
  if (!Buffer.isBuffer(request.rawBody)) {
    return Promise.reject(new ImageMealError('Multipart request body is required'));
  }

  return new Promise((resolve, reject) => {
    const parser = createMultipartParser(request);
    const fields = {};
    const images = [];
    let requestError;

    parser.on('field', (name, value, information) => {
      if (!REQUEST_FIELDS.has(name) || Object.hasOwn(fields, name) || information.valueTruncated) {
        requestError ??= new ImageMealError('Multipart fields are invalid');
        return;
      }
      fields[name] = value;
    });
    parser.on('file', (name, stream, information) => {
      if (name !== 'image') {
        requestError ??= new ImageMealError('Only meal images are allowed');
        stream.resume();
        return;
      }
      const imageChunks = [];
      const imageIndex = images.length;
      images.push(null);
      if (!SUPPORTED_IMAGE_MEDIA_TYPES.has(information.mimeType)) {
        requestError ??= new ImageMealError('Image media type is not supported', 415);
      }
      stream.on('data', (chunk) => imageChunks.push(chunk));
      stream.on('end', () => { images[imageIndex] = { data: Buffer.concat(imageChunks), mediaType: information.mimeType }; });
      stream.on('error', reject);
    });
    parser.on('fieldsLimit', () => {
      requestError ??= new ImageMealError('Multipart fields are invalid');
    });
    parser.on('error', () => reject(new ImageMealError('Multipart request is invalid')));
    parser.on('finish', () => {
      if (requestError) {
        reject(requestError);
        return;
      }
      if (images.length === 0 || images.some((image) => image.data.length === 0) || !fields.mealId || !fields.capturedAt) {
        reject(new ImageMealError('Meal image, meal ID, and captured time are required'));
        return;
      }
      resolve({
        mealId: fields.mealId,
        capturedAt: fields.capturedAt,
        images,
      });
    });
    parser.end(request.rawBody);
  });
}

module.exports = { parseImageMealRequest };

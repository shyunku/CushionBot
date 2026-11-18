export default class Http {
  static async get(url) {
    return new Promise((resolve, reject) => {
      try {
        if (!url.startsWith("/")) {
          url = `/${url}`;
        }
        url = `http://${botHost}:${botPort}${url}`;

        $.ajax({
          url,
          type: "GET",
          success: (data) => {
            resolve(data);
          },
          error: (err) => {
            reject(err);
          },
        });
      } catch (err) {
        reject(err);
      }
    });
  }
}

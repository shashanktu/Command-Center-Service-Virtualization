const axios = require('axios');
async function test() {
  try {
    const res = await axios.post('vam-service-virtualisation-ftada5d6eqgzaphb.eastus-01.azurewebsites.net/api/health-check', {
      url: 'vam-service-virtualisation-ftada5d6eqgzaphb.eastus-01.azurewebsites.net/api/mock/123',
      method: 'GET',
      headers: {},
      params: {}
    });
    console.log("Success:", res.data);
  } catch(e) {
    console.error("Error:", e.response ? e.response.status + " " + JSON.stringify(e.response.data) : e.message);
  }
}
test();

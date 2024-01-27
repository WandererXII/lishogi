lishogi.ratingDistributionChart = function (data) {
  // Translate function for internationalization
  const trans = lishogi.trans(data.i18n);

  // Load common chart script
  lishogi.loadScript('javascripts/chart/common.js').done(function () {

    // Load common chart features
    lishogi.chartCommon('highchart').done(function () {

      // Disable options
      const disabled = { enabled: false };
      const noText = { text: null };

      // Chart container
      $('#rating_distribution_chart').each(function () {
        const colors = Highcharts.getOptions().colors;

        // Function to calculate rating at a given index
        const ratingAt = function (i) {
          return 600 + i * 25;
        };

        // Function to calculate the sum of an array
        const arraySum = function (arr) {
          return arr.reduce(function (a, b) {
            return a + b;
          }, 0);
        };

        // Calculate the sum and cumulative percentage
        const sum = arraySum(data.freq);
        const cumul = [];
        for (let i = 0; i < data.freq.length; i++) {
          cumul.push(Math.round((arraySum(data.freq.slice(0, i)) / sum) * 100));
        }

        // Highcharts configuration
        $(this).highcharts({
          yAxis: {
            title: noText,
          },
          credits: disabled,
          legend: disabled,
          series: [
            {
              // Frequency distribution
              name: trans.noarg('players'),
              type: 'area',
              data: data.freq.map(function (nb, i) {
                return [ratingAt(i), nb];
              }),
              color: colors[1],
              fillColor: {
                linearGradient: {
                  x1: 0,
                  y1: 0,
                  x2: 0,
                  y2: 1.1,
                },
                stops: [
                  [0, colors[1]],
                  [1, Highcharts.Color(colors[1]).setOpacity(0).get('rgba')],
                ],
              },
              marker: {
                radius: 5,
              },
              lineWidth: 4,
            },
            {
              // Cumulative percentage
              name: trans.noarg('cumulative'),
              type: 'line',
              yAxis: 1,
              data: cumul.map(function (p, i) {
                return [ratingAt(i), p];
              }),
              color: Highcharts.Color(colors[11]).setOpacity(0.8).get('rgba'),
              marker: {
                radius: 1,
              },
              shadow: true,
              tooltip: {
                valueSuffix: '%',
              },
            },
          ],
          chart: {
            zoomType: 'xy',
            alignTicks: false,
          },
          plotOptions: {},
          title: noText,
          xAxis: {
            type: 'category',
            title: {
              text: trans.noarg('glicko2Rating'),
            },
            labels: {
              rotation: -45,
            },
            gridLineWidth: 1,
            tickInterval: 100,
            plotLines: [
              // Plot line for user's rating
              {
                label: {
                  text: trans.noarg('yourRating'),
                  verticalAlign: 'top',
                  align: data.myRating > 1800 ? 'right' : 'left',
                  y: 13,
                  x: data.myRating > 1800 ? -5 : 5,
                  style: {
                    color: colors[2],
                  },
                  rotation: 0,
                },
                dashStyle: 'dash',
                color: colors[2],
                width: 3,
                value: data.myRating,
              },
            ],
          },
          yAxis: [
            {
              // Frequency
              title: {
                text: trans.noarg('players'),
              },
            },
            {
              // Cumulative
              min: 0,
              max: 100,
              gridLineWidth: 0,
              title: {
                text: trans.noarg('cumulative'),
              },
              labels: {
                format: '{value}%',
              },
              opposite: true,
            },
          ],
        });
      });
    });
  });
};

import { rollupProject } from '@build/rollupProject';

export default rollupProject({
  main: {
    name: 'LishogiCoordinateTrainer',
    input: 'src/main.ts',
    output: 'coordinateTrainer',
  },
});

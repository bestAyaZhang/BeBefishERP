import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      colors: {
        ink: '#17202A',
        mist: '#F4F7F7',
        pine: '#0F766E',
        coral: '#D97757'
      }
    }
  },
  plugins: []
} satisfies Config;

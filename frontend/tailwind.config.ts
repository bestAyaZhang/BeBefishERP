import type { Config } from 'tailwindcss';
import defaultTheme from 'tailwindcss/defaultTheme';

export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Noto Sans SC Variable"', '"Noto Sans SC"', '"Inter Variable"', ...defaultTheme.fontFamily.sans],
        numeric: ['"Inter Variable"', '"Noto Sans SC Variable"', '"Noto Sans SC"', ...defaultTheme.fontFamily.sans]
      },
      fontSize: {
        xs: ['12px', { lineHeight: '18px' }],
        sm: ['14px', { lineHeight: '22px' }],
        caption: ['12px', { lineHeight: '18px', fontWeight: '400' }],
        label: ['12px', { lineHeight: '18px', fontWeight: '500' }],
        body: ['14px', { lineHeight: '22px', fontWeight: '400' }],
        'body-strong': ['14px', { lineHeight: '22px', fontWeight: '500' }],
        'card-title': ['15px', { lineHeight: '22px', fontWeight: '500' }],
        'section-title': ['16px', { lineHeight: '24px', fontWeight: '600' }],
        'page-title': ['24px', { lineHeight: '32px', fontWeight: '700' }],
        'table-number': ['14px', { lineHeight: '20px', fontWeight: '600' }]
      },
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

import React from 'react';

// The three groups twinkle out of phase.
export function renderStarField(className, stars) {
  return (
    <div className={`section-stars ${className}`} aria-hidden="true">
      {[0, 1, 2].map((group) => (
        <div className="section-star-group" key={group}>
          {stars.filter((star, index) => index % 3 === group).map(([x, y, size]) => (
            <img
              className="section-star"
              key={`${x}-${y}`}
              src="/assets/star.svg"
              alt=""
              style={{ '--star-x': x, '--star-y': y, '--star-size': size }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

export const introStars = [
  ['7%', '12%', '14px'], ['18%', '24%', '10px'], ['31%', '9%', '18px'],
  ['44%', '20%', '12px'], ['58%', '7%', '16px'], ['70%', '26%', '11px'],
  ['83%', '13%', '19px'], ['94%', '29%', '12px'], ['12%', '42%', '9px'],
  ['39%', '35%', '13px'], ['64%', '39%', '10px'], ['88%', '47%', '15px'],
];

export const partnerStars = [
  ['5%', '8%', '12px'], ['15%', '18%', '9px'], ['27%', '6%', '16px'],
  ['40%', '14%', '11px'], ['54%', '5%', '14px'], ['68%', '19%', '10px'],
  ['80%', '9%', '17px'], ['93%', '23%', '12px'], ['9%', '33%', '15px'],
  ['22%', '45%', '10px'], ['35%', '31%', '13px'], ['49%', '40%', '9px'],
  ['63%', '29%', '16px'], ['76%', '48%', '11px'], ['89%', '38%', '14px'],
  ['4%', '63%', '10px'], ['18%', '76%', '15px'], ['31%', '88%', '9px'],
  ['47%', '69%', '13px'], ['61%', '84%', '11px'], ['78%', '73%', '16px'],
  ['94%', '91%', '10px'],
];

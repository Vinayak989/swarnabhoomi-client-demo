export function isAvailableOffer(listing) {
  const original = Number(listing.pricePerUnit);
  const reduced = Number(listing.discountedPricePerUnit ?? listing.pricePerUnit);
  return listing.available !== false && Number(listing.quantity) > 0
    && Number(listing.discountPercent) > 0 && original > 0 && reduced >= 0 && reduced < original;
}
